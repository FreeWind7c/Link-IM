package com.link.restapi.module.chat.service;

import com.link.common.util.id.ChatIdGenerator;
import com.link.im.entity.chat.ChatSessionMember;
import com.link.im.entity.group.GroupInfo;
import com.link.im.enums.gloabl.GlobalCode;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.user.UserInfo;
import com.link.im.util.ApiResult;
import com.link.restapi.module.chat.model.dto.LinkCreateChatDto;
import com.link.restapi.module.chat.model.dto.LinkPullChatDTO;
import com.link.restapi.module.chat.model.vo.LinkChatSessionVo;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月22日
 */
@Component
public class ChatSessionService extends BasePlatFormMongoService<ChatSession> {
    public ApiResult pullChat(LinkPullChatDTO dto) {
        if (StringUtils.isEmpty(dto.getUserId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        if (!pageValidator(dto.getSkip(), dto.getLimit()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        // 1. 拉取该用户收件箱里的会话条目（分页），按最近活跃倒序
        Query memberQuery = eq(where(colOf(ChatSessionMember::getOwnerId)).is(new ObjectId(dto.getUserId())));
        memberQuery.with(Sort.by(
                Sort.Direction.DESC, colOf(ChatSessionMember::getUpdatedTime)));
        memberQuery.skip(dto.getSkip());
        memberQuery.limit(dto.getLimit());
        List<ChatSessionMember> chatSessionMembers = this.getMongoTemplate().find(memberQuery, ChatSessionMember.class);
        if (chatSessionMembers.isEmpty())
            return ApiResult.success().setData(new ArrayList<LinkChatSessionVo>());

        // 2. 按类型拆分：单聊取对端 UserInfo，群聊取 GroupInfo
        List<ObjectId> singleTargetIds = chatSessionMembers.stream()
                .filter(item -> item.getType() == ChatSessionMember.TYPE_SINGLE)
                .map(ChatSessionMember::getTargetId)
                .collect(Collectors.toList());
        List<ObjectId> groupTargetIds = chatSessionMembers.stream()
                .filter(item -> item.getType() == ChatSessionMember.TYPE_GROUP)
                .map(ChatSessionMember::getTargetId)
                .collect(Collectors.toList());

        // 3. 会话级信息（lastMsgSeq/summary/time、type），按 chatId 批量取
        List<String> chatIds = chatSessionMembers.stream()
                .map(ChatSessionMember::getChatId)
                .collect(Collectors.toList());
        Query sessionQuery = eq(where(col(ChatSession::getChatId)).in(chatIds));
        Map<String, ChatSession> sessionMap = this.find(sessionQuery).stream()
                .collect(Collectors.toMap(ChatSession::getChatId, Function.identity()));

        // 4. 单聊对端用户：昵称、头像
        Map<ObjectId, UserInfo> userMap;
        if (singleTargetIds.isEmpty()) {
            userMap = java.util.Collections.emptyMap();
        } else {
            Query userQuery = eq(where(colOf(UserInfo::getId)).in(singleTargetIds));
            userQuery.fields().include(colOf(UserInfo::getNickname), colOf(UserInfo::getAvatar));
            userMap = this.getMongoTemplate().find(userQuery, UserInfo.class).stream()
                    .collect(Collectors.toMap(UserInfo::getId, Function.identity()));
        }

        // 5. 群聊群信息：标题、头像
        Map<ObjectId, GroupInfo> groupMap;
        if (groupTargetIds.isEmpty()) {
            groupMap = java.util.Collections.emptyMap();
        } else {
            Query groupQuery = eq(where(colOf(GroupInfo::getId)).in(groupTargetIds));
            groupQuery.fields().include(colOf(GroupInfo::getTitle), colOf(GroupInfo::getAvatar));
            groupMap = this.getMongoTemplate().find(groupQuery, GroupInfo.class).stream()
                    .collect(Collectors.toMap(GroupInfo::getId, Function.identity()));
        }

        List<LinkChatSessionVo> vos = createChatInfoVos(chatSessionMembers, sessionMap, userMap, groupMap);
        return ApiResult.success().setData(vos);
    }

    private static List<LinkChatSessionVo> createChatInfoVos(List<ChatSessionMember> chatSessionMembers,
                                                             Map<String, ChatSession> sessionMap,
                                                             Map<ObjectId, UserInfo> userInfoMap,
                                                             Map<ObjectId, GroupInfo> groupInfoMap) {
        List<LinkChatSessionVo> vos = new ArrayList<>(chatSessionMembers.size());
        for (ChatSessionMember member : chatSessionMembers) {
            ChatSession session = sessionMap.get(member.getChatId());
            // 会话级信息缺失（理论上不该发生）时跳过，避免渲染出空会话
            if (session == null)
                continue;

            if (member.getType() == ChatSessionMember.TYPE_GROUP) {
                GroupInfo group = groupInfoMap.get(member.getTargetId());
                if (group == null)
                    continue;
                vos.add(new LinkChatSessionVo().createGroupVo(session, member, group));
            } else {
                UserInfo user = userInfoMap.get(member.getTargetId());
                if (user == null)
                    continue;
                vos.add(new LinkChatSessionVo().createSingleVo(session, member, user));
            }
        }
        return vos;
    }

    public ApiResult createChat(LinkCreateChatDto dto) {
        if (StringUtils.isEmpty(dto.getUserId()) || StringUtils.isEmpty(dto.getTargetId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        String chatId = ChatIdGenerator.nextId(dto.getUserId(), dto.getTargetId());
        ChatSession session = new ChatSession().createSingle(chatId);
        ChatSessionMember member = new ChatSessionMember().createSingle(dto.getUserId(), dto.getTargetId(), chatId);
        Query eq = eq(
                where(col(ChatSession::getChatId)).is(chatId)
        );
        Update update = update()
                .setOnInsert(col(ChatSession::getChatId), session.getChatId())
                .setOnInsert(col(ChatSession::getType), session.getType())
                .setOnInsert(col(ChatSession::getLastMsgSeq), session.getLastMsgSeq())
                .setOnInsert(col(ChatSession::getLastMsgSummary), session.getLastMsgSummary())
                .setOnInsert(col(ChatSession::getLastMsgTime), session.getLastMsgTime());
        FindAndModifyOptions options = FindAndModifyOptions.options()
                .upsert(true)
                .returnNew(true);
        this.findAndModify(eq,update,options);
        this.getMongoTemplate().insert(member);
        return ApiResult.success();
    }


}
