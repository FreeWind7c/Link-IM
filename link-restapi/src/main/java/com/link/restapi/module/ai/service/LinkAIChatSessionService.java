package com.link.restapi.module.ai.service;

import com.link.base.entity.chat.ChatSession;
import com.link.base.entity.chat.ChatSessionMember;
import com.link.base.entity.friend.FriendInfo;
import com.link.base.entity.group.GroupInfo;
import com.link.base.entity.group.GroupMember;
import com.link.base.entity.user.UserInfo;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.restapi.module.ai.model.dto.LinkResolveChatSessionDTO;
import com.link.restapi.module.ai.model.vo.LinkResolvedChatSessionVO;
import com.link.restapi.utils.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 会话服务
 * 负责根据会话名称解析出真实的 chatId
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */
@Slf4j
@Component
public class LinkAIChatSessionService extends BasePlatFormMongoService<ChatSession> {

    /**
     * 根据会话名称解析出 chatId
     *
     * 正确的查询逻辑：
     * 1. 先查询当前用户的会话列表 (ChatSessionMember where ownerId=当前用户)
     * 2. 在会话列表中匹配名称：
     *    - 单聊：通过 targetId 关联 UserInfo，匹配昵称或好友备注
     *    - 群聊：通过 targetId 关联 GroupInfo，匹配群名或群备注
     *
     * @param dto 包含 userId, sessionName, sessionType
     * @return 解析结果，包含 chatId, targetId, displayName 等
     */
    public ApiResult resolveChatSession(LinkResolveChatSessionDTO dto) {
        // 参数校验
        if (!stringValidator(dto.getUserId(), dto.getSessionName()) || !sessionTypeValidator(dto.getSessionType())) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        ObjectId userId = new ObjectId(dto.getUserId());
        String sessionName = dto.getSessionName().trim();
        int sessionType = dto.getSessionType();

        try {
            LinkResolvedChatSessionVO result;

            if (sessionType == ChatSession.TYPE_GROUP) {
                // 群聊查询
                result = resolveGroupChat(userId, sessionName);
            } else {
                // 单聊查询
                result = resolveSingleChat(userId, sessionName);
            }

            if (result == null) {
                return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR.getCode(),
                    String.format("在你的会话列表中未找到名称为 '%s' 的%s", sessionName, sessionType == 2 ? "群聊" : "单聊"));
            }

            return ApiResult.success().setData(result);

        } catch (Exception e) {
            log.error("解析会话失败: userId={}, sessionName={}, sessionType={}",
                dto.getUserId(), sessionName, sessionType, e);
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR.getCode(), "解析会话失败: " + e.getMessage());
        }
    }

    /**
     * 解析单聊会话
     * 1. 查询当前用户的所有单聊会话 (ChatSessionMember where ownerId=当前用户 and type=1)
     * 2. 遍历每个会话，通过 targetId 查询对方用户信息
     * 3. 匹配昵称或好友备注
     */
    private LinkResolvedChatSessionVO resolveSingleChat(ObjectId userId, String sessionName) {
        // 1. 查询当前用户的所有单聊会话
        Query memberQuery = new Query(Criteria.where("owner_id").is(userId)
            .and("type").is(ChatSession.TYPE_SINGLE));
        List<ChatSessionMember> singleChats = getMongoTemplate().find(memberQuery, ChatSessionMember.class);

        if (singleChats.isEmpty()) {
            log.info("用户没有单聊会话: userId={}", userId.toHexString());
            return null;
        }

        // 2. 遍历每个会话，查询对方信息并匹配名称
        for (ChatSessionMember member : singleChats) {
            ObjectId friendId = member.getTargetId();

            // 查询对方用户信息
            UserInfo friendInfo = getMongoTemplate().findById(friendId, UserInfo.class);
            if (friendInfo == null) {
                continue;
            }

            // 匹配昵称
            if (sessionName.equals(friendInfo.getNickname())) {
                return new LinkResolvedChatSessionVO()
                    .setChatId(member.getChatId())
                    .setSessionType(ChatSession.TYPE_SINGLE)
                    .setDisplayName(friendInfo.getNickname())
                    .setTargetId(friendId.toHexString())
                    .setMatchedBy("通过用户昵称匹配");
            }

            // 匹配好友备注
            Query friendQuery = new Query(Criteria.where("user_id").is(userId)
                .and("friend_id").is(friendId)
                .and("status").is(1));
            FriendInfo friendship = getMongoTemplate().findOne(friendQuery, FriendInfo.class);

            if (friendship != null && sessionName.equals(friendship.getRemark())) {
                return new LinkResolvedChatSessionVO()
                    .setChatId(member.getChatId())
                    .setSessionType(ChatSession.TYPE_SINGLE)
                    .setDisplayName(friendInfo.getNickname())
                    .setTargetId(friendId.toHexString())
                    .setMatchedBy("通过好友备注匹配");
            }
        }

        return null;
    }

    /**
     * 解析群聊会话
     * 1. 查询当前用户的所有群聊会话 (ChatSessionMember where ownerId=当前用户 and type=2)
     * 2. 遍历每个会话，通过 targetId 查询群信息
     * 3. 匹配群名或群备注
     */
    private LinkResolvedChatSessionVO resolveGroupChat(ObjectId userId, String sessionName) {
        // 1. 查询当前用户的所有群聊会话
        Query memberQuery = new Query(Criteria.where("owner_id").is(userId)
            .and("type").is(ChatSession.TYPE_GROUP));
        List<ChatSessionMember> groupChats = getMongoTemplate().find(memberQuery, ChatSessionMember.class);

        if (groupChats.isEmpty()) {
            log.info("用户没有群聊会话: userId={}", userId.toHexString());
            return null;
        }

        // 2. 遍历每个会话，查询群信息并匹配名称
        for (ChatSessionMember member : groupChats) {
            ObjectId groupId = member.getTargetId();

            // 查询群信息
            GroupInfo groupInfo = getMongoTemplate().findById(groupId, GroupInfo.class);
            if (groupInfo == null) {
                continue;
            }

            // 匹配群名
            if (sessionName.equals(groupInfo.getTitle())) {
                return new LinkResolvedChatSessionVO()
                    .setChatId(member.getChatId())
                    .setSessionType(ChatSession.TYPE_GROUP)
                    .setDisplayName(groupInfo.getTitle())
                    .setTargetId(groupId.toHexString())
                    .setMatchedBy("通过群名匹配");
            }

            // 匹配群备注
            Query groupMemberQuery = new Query(Criteria.where("user_id").is(userId)
                .and("group_id").is(groupId));
            GroupMember groupMembership = getMongoTemplate().findOne(groupMemberQuery, GroupMember.class);

            if (groupMembership != null && sessionName.equals(groupMembership.getRemark())) {
                return new LinkResolvedChatSessionVO()
                    .setChatId(member.getChatId())
                    .setSessionType(ChatSession.TYPE_GROUP)
                    .setDisplayName(groupInfo.getTitle())
                    .setTargetId(groupId.toHexString())
                    .setMatchedBy("通过群备注匹配");
            }
        }

        return null;
    }
}
