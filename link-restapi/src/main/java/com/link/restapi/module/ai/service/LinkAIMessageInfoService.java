package com.link.restapi.module.ai.service;

import com.link.base.entity.base.BaseMessage;
import com.link.base.entity.chat.ChatSession;
import com.link.base.entity.chat.ChatSessionMember;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.entity.message.GroupMessageInfo;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.restapi.enums.chat.ChatSessionCode;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.restapi.module.ai.model.dto.LinkSearchMessageDTO;
import com.link.restapi.module.message.model.dto.LinkAIPullMessageDTO;
import com.link.restapi.module.message.model.dto.LinkPullMessageByChatDTO;
import com.link.restapi.module.message.service.LinkMessageInfoService;
import com.link.restapi.utils.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月26日
 */
@Slf4j
@Component
public class LinkAIMessageInfoService extends BasePlatFormMongoService<DefaultMessageInfo> {

    @Autowired
    private LinkMessageInfoService messageInfoService;

    public ApiResult pullMessage(LinkAIPullMessageDTO dto) {
        if (!stringValidator(dto.getUserId())
                || (dto.getSessionType() != 1 && dto.getSessionType() != 2)
                || dto.getLimit() <= 0
                || dto.getStartTime() <= 0
                || dto.getEndTime() <=0)
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        List<String> chatId = this.getMongoTemplate().find(
                eq(where(col(ChatSessionMember::getOwnerId)).is(new ObjectId(dto.getUserId()))),
                ChatSessionMember.class
        ).stream().map(v -> { return v.getChatId();
        }).collect(Collectors.toList());

        Criteria criteria = where(col(BaseMessage::getChatId))
                .in(chatId)
                .and(col(BaseMessage::getTimestamp))
                .gte(dto.getStartTime())
                .lte(dto.getEndTime());
        Query query = eq(criteria);
        query.with(Sort.by(Sort.Direction.DESC,col(BaseMessage::getTimestamp)));
        query.limit(dto.getLimit());


        return ApiResult.success().setData(dto.getSessionType() == 1
                ? messageInfoService.createVo(this.getMongoTemplate().find(query,DefaultMessageInfo.class))
                : messageInfoService.createGroupVo(this.getMongoTemplate().find(query,GroupMessageInfo.class)));
    }

    /**
     * 根据 chatId 查询消息记录
     *
     * @param dto 包含 userId, chatId, limit
     * @return 消息列表
     */
    public ApiResult pullMessageByChat(LinkPullMessageByChatDTO dto) {
        // 参数校验
        if (!stringValidator(dto.getUserId(), dto.getChatId()) || dto.getLimit() <= 0) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        try {
            ObjectId userId = new ObjectId(dto.getUserId());
            String chatId = dto.getChatId();

            // 1. 判断会话类型（从 chatId 格式判断）
            ChatSession session = this.getMongoTemplate().findOne(eq(where(col(ChatSession::getChatId)).is(dto.getChatId())), ChatSession.class);
            if(session == null)
                return ApiResult.error(ChatSessionCode.CHAT_SESSION_NOT_EXIST);

            int sessionType = session.getType();


            // 2. 查询消息（不验证 ChatSessionMember，因为 AI 查询时可能会话还未创建）
            Query messageQuery = new Query(Criteria.where("chat_id").is(chatId));
            messageQuery.with(Sort.by(Sort.Direction.DESC, "timestamp"));
            messageQuery.limit(dto.getLimit());

            List<?> messages;
            if (sessionType == ChatSession.TYPE_SINGLE) {
                // 单聊消息
                messages = getMongoTemplate().find(messageQuery, DefaultMessageInfo.class);
                return ApiResult.success().setData(messageInfoService.createVo((List<DefaultMessageInfo>) messages));
            } else {
                // 群聊消息
                messages = getMongoTemplate().find(messageQuery, GroupMessageInfo.class);
                return ApiResult.success().setData(messageInfoService.createGroupVo((List<GroupMessageInfo>) messages));
            }

        } catch (Exception e) {
            log.error("查询会话消息失败: userId={}, chatId={}", dto.getUserId(), dto.getChatId(), e);
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR.getCode(),
                "查询消息失败: " + e.getMessage());
        }
    }

    /**
     * 搜索消息
     * 支持按关键词、时间范围、发送者等条件搜索消息
     *
     * @param dto 搜索条件
     * @return 消息列表
     */
    public ApiResult searchMessage(LinkSearchMessageDTO dto) {
        // 参数校验
        if (!stringValidator(dto.getUserId(), dto.getChatId())
            || dto.getLimit() < 1
            || (dto.getType() != 1 && dto.getType() != 2)) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        try {
            // 构建查询条件
            Criteria criteria = Criteria.where("chat_id").is(dto.getChatId());

            // 时间范围
            if (dto.getStartTime() != null && dto.getStartTime() > 0) {
                criteria.and("timestamp").gte(dto.getStartTime());
            }
            if (dto.getEndTime() != null && dto.getEndTime() > 0) {
                criteria.and("timestamp").lte(dto.getEndTime());
            }

            // 发送者筛选
            if (stringValidator(dto.getSenderId())) {
                criteria.and("snd_id").is(new ObjectId(dto.getSenderId()));
            }

            Query query = new Query(criteria);
            query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
            query.limit(dto.getLimit());

            // 查询消息
            List<?> messages;
            if (dto.getType() == ChatSession.TYPE_SINGLE) {
                messages = getMongoTemplate().find(query, DefaultMessageInfo.class);
                List<DefaultMessageInfo> resultList = (List<DefaultMessageInfo>) messages;

                // 关键词过滤（在内存中过滤）
                if (stringValidator(dto.getKeyword())) {
                    String keyword = dto.getKeyword().toLowerCase();
                    resultList = resultList.stream()
                        .filter(msg -> {
                            try {
                                // 解析 data 字段中的 content
                                if (msg.getData() != null && msg.getData().contains("content")) {
                                    return msg.getData().toLowerCase().contains(keyword);
                                }
                                return false;
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .collect(Collectors.toList());
                }

                return ApiResult.success().setData(messageInfoService.createVo(resultList));
            } else {
                messages = getMongoTemplate().find(query, GroupMessageInfo.class);
                List<GroupMessageInfo> resultList = (List<GroupMessageInfo>) messages;

                // 关键词过滤（在内存中过滤）
                if (stringValidator(dto.getKeyword())) {
                    String keyword = dto.getKeyword().toLowerCase();
                    resultList = resultList.stream()
                        .filter(msg -> {
                            try {
                                // 解析 data 字段中的 content
                                if (msg.getData() != null && msg.getData().contains("content")) {
                                    return msg.getData().toLowerCase().contains(keyword);
                                }
                                return false;
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .collect(Collectors.toList());
                }

                return ApiResult.success().setData(messageInfoService.createGroupVo(resultList));
            }

        } catch (Exception e) {
            log.error("搜索消息失败: userId={}, chatId={}, keyword={}",
                dto.getUserId(), dto.getChatId(), dto.getKeyword(), e);
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR.getCode(),
                "搜索消息失败: " + e.getMessage());
        }
    }

    /**
     * 调试方法：检查消息数据
     * 用于排查为什么查询不到消息
     */
    public ApiResult debugMessages(String userId, String friendId) {
        try {
            ObjectId userObjId = new ObjectId(userId);
            ObjectId friendObjId = new ObjectId(friendId);

            // 构造预期的 chatId
            String expectedChatId;
            if (userId.compareTo(friendId) < 0) {
                expectedChatId = "single_" + userId + "_" + friendId;
            } else {
                expectedChatId = "single_" + friendId + "_" + userId;
            }

            // 1. 查询该 chatId 的消息数量
            Query chatIdQuery = new Query(Criteria.where("chat_id").is(expectedChatId));
            long countByChatId = getMongoTemplate().count(chatIdQuery, DefaultMessageInfo.class);

            // 2. 查询用户发送的所有消息（前10条）
            Query userSendQuery = new Query(Criteria.where("snd_id").is(userObjId));
            userSendQuery.limit(10);
            List<DefaultMessageInfo> userSentMessages = getMongoTemplate().find(userSendQuery, DefaultMessageInfo.class);

            // 3. 查询用户接收的所有消息（前10条）
            Query userReceiveQuery = new Query(Criteria.where("rcv_id").is(userObjId));
            userReceiveQuery.limit(10);
            List<DefaultMessageInfo> userReceivedMessages = getMongoTemplate().find(userReceiveQuery, DefaultMessageInfo.class);

            // 4. 查询所有不同的 chatId
            List<String> allChatIds = getMongoTemplate().findDistinct(
                new Query(), "chat_id", DefaultMessageInfo.class, String.class);

            // 5. 检查 ChatSessionMember
            Query memberQuery = new Query(Criteria.where("owner_id").is(userObjId)
                .and("chat_id").is(expectedChatId));
            ChatSessionMember member = getMongoTemplate().findOne(memberQuery, ChatSessionMember.class);

            // 构造调试信息
            java.util.Map<String, Object> debugInfo = new java.util.HashMap<>();
            debugInfo.put("expectedChatId", expectedChatId);
            debugInfo.put("messageCountByChatId", countByChatId);
            debugInfo.put("userSentMessagesCount", userSentMessages.size());
            debugInfo.put("userReceivedMessagesCount", userReceivedMessages.size());
            debugInfo.put("chatSessionMemberExists", member != null);
            debugInfo.put("allChatIdsCount", allChatIds.size());
            debugInfo.put("allChatIdsSample", allChatIds.stream().limit(10).collect(Collectors.toList()));
            debugInfo.put("userSentMessagesSample", userSentMessages.stream()
                .map(m -> java.util.Map.of(
                    "chatId", m.getChatId(),
                    "sndId", m.getSndId().toHexString(),
                    "rcvId", m.getRcvId().toHexString(),
                    "timestamp", m.getTimestamp()
                ))
                .collect(Collectors.toList()));

            return ApiResult.success().setData(debugInfo);

        } catch (Exception e) {
            log.error("调试查询失败: userId={}, friendId={}", userId, friendId, e);
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR.getCode(),
                "调试查询失败: " + e.getMessage());
        }
    }

}