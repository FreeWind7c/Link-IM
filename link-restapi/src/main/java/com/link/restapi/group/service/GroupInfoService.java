package com.link.restapi.group.service;

import com.link.common.core.event.EventType;
import com.link.common.core.model.group.LinkJoinGroup;
import com.link.common.im.data.LinkChatSession;
import com.link.common.redis.RedisConstant;
import com.link.common.util.id.ChatIdGenerator;
import com.link.im.constants.group.GroupRoleConstant;
import com.link.im.entity.chat.ChatSessionMember;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.group.GroupInfo;
import com.link.im.entity.group.GroupMember;
import com.link.im.enums.gloabl.GlobalCode;
import com.link.im.enums.group.GroupInfoCode;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.util.ApiResult;
import com.link.restapi.group.model.dto.LinkAddGroupAdministratorDto;
import com.link.restapi.group.model.dto.LinkCreateGroupDto;
import com.link.restapi.group.model.dto.LinkJoinGroupDto;
import com.link.restapi.push.RemotePushPublisher;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月26日
 */
@Slf4j
@Component
public class GroupInfoService extends BasePlatFormMongoService<GroupInfo> {


    @Autowired
    private GroupMemberService groupMemberService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RemotePushPublisher pushPublisher;

    @Transactional(rollbackFor = Exception.class)
    public ApiResult createGroup(LinkCreateGroupDto dto) {
        if (!stringValidator(dto.getOwnerId()) || dto.getMemebrs().size() < 1)
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        List<GroupMember> members = this.groupMemberService.createMembers(dto);
        List<ObjectId> memberIds = members.stream().map(item -> { return item.getUserId();
        }).collect(Collectors.toList());

        GroupInfo groupInfo = new GroupInfo().create(dto.getOwnerId(), dto.getOwnerName(), memberIds);
        GroupInfo group = this.insert(groupInfo);
        members.forEach(item -> {item.setGroupId(group.getId());});
        this.getMongoTemplate().insert(members,GroupMember.class);

        String chatId = ChatIdGenerator.nextId(group.getId().toHexString());
        ChatSession session = new ChatSession().createGroup(chatId);
        List<ChatSessionMember> chatSessionMembers = members.stream().map(item -> {
            return new ChatSessionMember().createGroup(item.getUserId().toHexString(), group.getId().toHexString(), chatId);
        }).collect(Collectors.toList());

        this.getMongoTemplate().insert(session);
        this.getMongoTemplate().insert(chatSessionMembers, ChatSessionMember.class);
        return ApiResult.success(GroupInfoCode.GROUP_CREATE_SUCCESS);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResult joinGroup(LinkJoinGroupDto dto) {
        // 参数校验
        if (!stringValidator(dto.getInviterUserId())
                || !stringValidator(dto.getGroupId())
                || dto.getUserIds() == null
                || dto.getUserIds().isEmpty()
                || dto.getSource() < 1) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        // 查询群信息
        GroupInfo group = this.findOne(
                eq(where(col(GroupInfo::getId)).is(new ObjectId(dto.getGroupId())))
        );

        if (group == null) {
            return ApiResult.error(GroupInfoCode.GROUP_NOT_EXIST);
        }

        // 判断邀请人是否在群内
        ChatSessionMember inviterMember = this.getMongoTemplate().findOne(
                eq(where(col(ChatSessionMember::getOwnerId)).is(new ObjectId(dto.getInviterUserId()))
                        .and(col(ChatSessionMember::getTargetId)).is(group.getId())),
                ChatSessionMember.class
        );

        if (inviterMember == null) {
            return ApiResult.error(GlobalCode.NO_PERMISSION);
        }

        // 查询会话
        ChatSession session = this.getMongoTemplate().findOne(
                eq(where(col(ChatSession::getChatId)).is(inviterMember.getChatId())),
                ChatSession.class
        );

        if (session == null) {
            return ApiResult.error(GroupInfoCode.GROUP_NOT_EXIST);
        }

        // 查询已经存在的群成员
        List<GroupMember> existMembers = this.getMongoTemplate().find(
                eq(where(col(GroupMember::getGroupId)).is(group.getId())
                        .and(col(GroupMember::getUserId)).in(
                                dto.getUserIds().stream()
                                        .map(ObjectId::new)
                                        .collect(Collectors.toList())
                        )),
                GroupMember.class
        );

        Set<ObjectId> existUserIds = existMembers.stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toSet());

        List<GroupMember> groupMembers = new ArrayList<>();
        List<ChatSessionMember> chatSessionMembers = new ArrayList<>();

        for (String userId : dto.getUserIds()) {

            ObjectId uid = new ObjectId(userId);
            if (existUserIds.contains(uid)) {
                continue;
            }

            groupMembers.add(new GroupMember().create(group.getId().toHexString(),userId,dto.getInviterUserId(),GroupRoleConstant.REGULAR_MEMBER));
            chatSessionMembers.add(new ChatSessionMember().createGroup(userId,group.getId().toHexString(),session.getChatId()));
        }

        if (groupMembers.isEmpty()) {
            return ApiResult.success();
        }

        this.getMongoTemplate().insert(groupMembers, GroupMember.class);
        this.getMongoTemplate().insert(chatSessionMembers, ChatSessionMember.class);

        // 更新成员数量
        this.updateFirst(
                eq(where(col(GroupInfo::getId)).is(group.getId())),
                update().inc(col(GroupInfo::getGroupMemberSize), groupMembers.size())
        );
        // 失效群成员缓存，下一条群消息由 LinkGroupMessageProcessor 懒加载重建
        redisTemplate.delete(RedisConstant.GROUP_MEMBER + session.getChatId());

        // 逐个新成员推送：每人的会话视图（ownerId）不同，需各自构建一份 LinkChatSession 下发
        for (ChatSessionMember member : chatSessionMembers) {
            LinkJoinGroup payload = new LinkJoinGroup()
                    .setSession(toLinkChatSession(session, member, group));
            this.pushPublisher.push(Arrays.asList(member.getOwnerId().toHexString()), EventType.JOIN_GROUP, payload);
        }
        return ApiResult.success();
    }

    private LinkChatSession toLinkChatSession(ChatSession session, ChatSessionMember member, GroupInfo group) {
        return new LinkChatSession()
                .setChatId(session.getChatId())
                .setType(session.getType())
                .setTitle(group.getTitle())
                .setAvatar(group.getAvatar())
                .setOwnerId(member.getOwnerId().toHexString())
                .setTargetId(member.getTargetId().toHexString())
                .setLastMsgSummary(session.getLastMsgSummary())
                .setUnreadCount(session.getLastMsgSeq() - member.getLastReadSeq())
                .setLastReadSeq(member.getLastReadSeq())
                .setLastMsgSeq(session.getLastMsgSeq())
                .setLastMsgTime(session.getLastMsgTime())
                .setShowTop(member.isShowTop())
                .setSilence(member.isSilence())
                .setHidden(member.isHidden())
                .setActive(member.isActive());
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResult addAdministrator(LinkAddGroupAdministratorDto dto) {

        // 参数校验
        if (!stringValidator(dto.getUserId())
                || !stringValidator(dto.getGroupId())
                || dto.getAdministratorId() == null
                || dto.getAdministratorId().isEmpty()) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        // 判断群是否存在
        GroupInfo group = this.findOne(
                eq(where(col(GroupInfo::getId)).is(new ObjectId(dto.getGroupId())))
        );

        if (group == null) {
            return ApiResult.error(GroupInfoCode.GROUP_NOT_EXIST);
        }

        // 判断当前用户权限（管理员/群主）
        GroupMember operator = this.getMongoTemplate().findOne(
                eq(
                        where(col(GroupMember::getGroupId)).is(group.getId())
                                .and(col(GroupMember::getUserId)).is(new ObjectId(dto.getUserId()))
                ),
                GroupMember.class
        );

        if (operator == null
                || operator.getRole() < GroupRoleConstant.ADMINISTRATOR) {
            return ApiResult.error(GlobalCode.NO_PERMISSION);
        }

        // 转 ObjectId
        List<ObjectId> adminIds = dto.getAdministratorId()
                .stream()
                .map(ObjectId::new)
                .toList();

        // 查询这些用户是否都是群成员
        List<GroupMember> members = this.getMongoTemplate().find(
                eq(
                        where(col(GroupMember::getGroupId)).is(group.getId())
                                .and(col(GroupMember::getUserId)).in(adminIds)
                ),
                GroupMember.class
        );

        if (members.size() != adminIds.size()) {
            return ApiResult.error(GroupInfoCode.GROUP_MEMBER_NOT_EXIST);
        }

        // 修改角色
        Update updateRole = new Update()
                .set(col(GroupMember::getRole), GroupRoleConstant.ADMINISTRATOR);

        this.getMongoTemplate().updateMulti(
                eq(
                        where(col(GroupMember::getGroupId)).is(group.getId())
                                .and(col(GroupMember::getUserId)).in(adminIds)
                ),
                updateRole,
                GroupMember.class
        );

        // 更新管理员列表（Mongo去重）
        Update updateAdmin = new Update()
                .addToSet(col(GroupInfo::getAdminUserIds))
                .each(dto.getAdministratorId().toArray());

        this.updateFirst(
                eq(where(col(GroupInfo::getId)).is(group.getId())),
                updateAdmin
        );

        return ApiResult.success();
    }
}
