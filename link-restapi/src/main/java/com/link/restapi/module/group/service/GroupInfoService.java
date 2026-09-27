package com.link.restapi.module.group.service;


import com.google.gson.Gson;
import com.link.base.seq.MessageSeqAllocator;
import com.link.common.core.event.EventType;
import com.link.common.core.model.group.LinkJoinGroup;
import com.link.common.core.model.group.LinkRemoveGroup;
import com.link.common.im.data.LinkChatSession;
import com.link.common.redis.RedisKeys;
import com.link.common.util.id.ChatIdGenerator;

import com.link.common.constants.group.GroupRoleConstant;
import com.link.base.entity.chat.ChatSessionMember;
import com.link.base.entity.chat.ChatSession;
import com.link.base.entity.data.NoticeData;
import com.link.base.entity.data.notice.JoinGroupNoticeData;
import com.link.base.entity.group.GroupInfo;
import com.link.base.entity.group.GroupMember;
import com.link.base.entity.message.GroupMessageInfo;
import com.link.base.entity.message.type.MessageType;
import com.link.base.entity.user.UserInfo;
import com.link.base.repository.ChatSessionRepository;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.restapi.enums.group.GroupApiCode;
import com.link.base.facotry.LinkBaseDataFactory;
import com.link.base.facotry.LinkMessageFactory;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.base.manager.CacheDataManager;
import com.link.restapi.utils.ApiResult;
import com.link.base.vo.GroupMessageVO;
import com.link.restapi.module.group.model.dto.LinkAddGroupAdministratorDto;
import com.link.restapi.module.group.model.dto.LinkCreateGroupDto;
import com.link.restapi.module.group.model.dto.LinkJoinGroupDto;
import com.link.restapi.module.group.model.dto.LinkRemoveGroupMemberDto;
import com.link.restapi.push.RemotePushPublisher;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
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
    private MessageSeqAllocator messageSeqAllocator;

    /** 读会话级 seq 计数器（seq:{chatId}）用，纯字符串数字，不能用 JDK 序列化的 redisTemplate。 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RemotePushPublisher pushPublisher;

    @Autowired
    private CacheDataManager cacheManager;

    @Autowired
    private LinkBaseDataFactory dataFactory;

    @Autowired
    private LinkMessageFactory messageFactory;

    @Autowired
    private ChatSessionRepository sessionRepository;

    @Autowired
    private GroupTxExecutorService tx;

    @Transactional(rollbackFor = Exception.class)
    public ApiResult createGroup(LinkCreateGroupDto dto) {
        if (!stringValidator(dto.getOwnerId()) || dto.getMemebrs().size() < 1)
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        List<String>  userIds = dto.getMemebrs().stream().map(item -> { return item.getUserId();
        }).collect(Collectors.toList());
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
        System.out.println("2");
        NoticeData noticeData = this.dataFactory.createGroupNoticeData(chatId, dto.getOwnerId());
        GroupMessageInfo messageInfo = (GroupMessageInfo) messageFactory.create(session.getChatId(), new ObjectId(dto.getOwnerId()), group.getId(), noticeData.getMessageType()
                , noticeData, GroupMessageInfo.class);
        GroupMessageVO vo = messageInfo.toVo();
        this.pushPublisher.messageStorage(vo,vo.getMessageType());
        this.sessionRepository.updateSession(messageInfo);
        this.pushPublisher.push(EventType.GROUP_MESSAGE,Arrays.asList(dto.getOwnerId()),vo);


        return ApiResult.success().setMsg(GroupApiCode.GROUP_CREATE_SUCCESS.getMessage());
    }

    public ApiResult addAdministrator(LinkAddGroupAdministratorDto dto) {

        // 参数校验
        if (!stringValidator(dto.getUserId())
                || !stringValidator(dto.getGroupId())
                || dto.getAdministratorId() == null
                || dto.getAdministratorId().isEmpty()) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        // 判断群是否存在
        GroupInfo group = this.getMongoTemplate().findOne(
                eq(where(col(GroupInfo::getId)).is(new ObjectId(dto.getGroupId()))),
                GroupInfo.class
        );

        if (group == null) {
            return ApiResult.error(GroupApiCode.GROUP_NOT_EXIST);
        }

        // 判断当前用户权限（管理员/群主）
        GroupMember operator = this.getMongoTemplate().findOne(
                eq(
                        where(col(GroupMember::getGroupId)).is(group.getId())
                                .and(col(GroupMember::getUserId)).is(new ObjectId(dto.getUserId()))
                ),
                GroupMember.class
        );

        if (operator == null || operator.getRole() > GroupRoleConstant.ADMINISTRATOR) {
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
            return ApiResult.error(GroupApiCode.GROUP_MEMBER_NOT_EXIST);
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


    /**
     * 移除群成员
     */
    public ApiResult removeMember(LinkRemoveGroupMemberDto dto) {
        // 1. 参数校验
        if (!stringValidator(dto.getGroupId(), dto.getOperationUserId())
                || CollectionUtils.isEmpty(dto.getRemovedUserId())) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        if (!ObjectId.isValid(dto.getGroupId())) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        List<ObjectId> removedUserIds = dto.getRemovedUserId().stream()
                .filter(StringUtils::hasText)
                .distinct()
                .map(ObjectId::new)
                .toList();

        if (removedUserIds.isEmpty()) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        // 2. 权限验证
        ApiResult permissionResult = validateRemovePermission(dto, removedUserIds);
        if (permissionResult != null) {
            return permissionResult;
        }

        // 3. 执行事务操作（数据库写入）
        GroupTxExecutorService.RemoveMemberContext context = tx.executeRemoveMemberTx(dto, removedUserIds);
        // 删除缓存
//        cacheManager.removeCache(RedisKeys.GROUP_MEMBER + dto.getGroupId());

        // 推送移除事件给被移除的成员
        this.pushPublisher.push(EventType.REMOVE_GROUP_MEMBER, dto.getRemovedUserId(), new LinkRemoveGroup(context.getChatId()));

        if (context.getRemovedCount() == 0) {
            return ApiResult.success();
        }

        GroupInfo group = this.findById(new ObjectId(dto.getGroupId()));

        // 4. 事务提交后：查询群成员并推送通知
        afterRemoveMemberCommit(context,group, dto);

        return ApiResult.success();
    }

    /**
     * 加入群聊
     */
    public ApiResult joinGroup(LinkJoinGroupDto dto) {
        // 1. 参数校验
        if (!stringValidator(dto.getInviterUserId())
                || !stringValidator(dto.getGroupId())
                || dto.getUserIds() == null
                || dto.getUserIds().isEmpty()
                || dto.getSource() < 1) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        // 2. 验证群和权限
        boolean isGroup = validateGroupAndPermission(dto.getGroupId(), dto.getInviterUserId());
        if (!isGroup)
            return ApiResult.error(GroupApiCode.GROUP_NOT_EXIST);

        ChatSession session = findSession(dto);
        if (session == null)
            return ApiResult.error(GroupApiCode.GROUP_NOT_EXIST);

        // 4. 执行事务操作（数据库写入）
        GroupTxExecutorService.JoinGroupContext context = tx.executeJoinGroupTx(dto,session);
        GroupInfo group = this.findById(new ObjectId(dto.getGroupId()));

        // 删除缓存
//        cacheManager.removeCache(RedisKeys.GROUP_MEMBER + dto.getGroupId());

        // 推送单个用户的加入事件
        for (ChatSessionMember member : context.getPushMembers()) {
            LinkJoinGroup payload = new LinkJoinGroup().setSession(toLinkChatSession(session, member, group));
            this.pushPublisher.push(EventType.JOIN_GROUP, Arrays.asList(member.getOwnerId().toHexString()), payload);
        }

        if (context.getNewMembersCount() == 0) {
            return ApiResult.success();
        }

        // 5. 事务提交后：查询群成员并推送通知
        afterJoinGroupCommit(context, group,dto);
        return ApiResult.success();
    }

    private ChatSession findSession(LinkJoinGroupDto dto) {
        // 根据 groupId 查询任意一个群成员记录
        ChatSessionMember anyMember = this.getMongoTemplate().findOne(
            eq(where(col(ChatSessionMember::getTargetId)).is(new ObjectId(dto.getGroupId()))),
            ChatSessionMember.class
        );

        if (anyMember == null) {
            return null;
        }

        // 通过 chatId 查询会话信息
        return this.getMongoTemplate().findOne(
            eq(where(col(ChatSession::getChatId)).is(anyMember.getChatId())),
            ChatSession.class
        );
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


    /**
     * 事务提交后：查询最新群成员并推送群通知
     */
    private void afterJoinGroupCommit(GroupTxExecutorService.JoinGroupContext context, GroupInfo group, LinkJoinGroupDto dto) {
        String chatId = context.getSession().getChatId();
        ObjectId groupId = group.getId();
//        Query query = eq(where(col(GroupMember::getGroupId)).is(group.getId()));
//        query.fields().include(col(GroupMember::getUserId));
//        List<String> memberIds = this.getPrimaryMongoTemplate().find(query, GroupMember.class).stream().map(v -> {
//            return v.getUserId().toHexString();
//        }).collect(Collectors.toList()); cacheManager.getGroupMemberId(group.getId().toHexString());
        Set<String> memberId = null;
        pushPublisher.push(
                EventType.GROUP_MESSAGE,
                memberId,
                createJoinGroupNotice(groupId, chatId, dto.getUserIds(), dto.getInviterUserId())
        );
    }

    private GroupMessageInfo createJoinGroupNotice(ObjectId groupId, String chatId, List<String> userId, String inviterUserId) {
        Map<ObjectId, String> nameMap = queryNicknames(userId, inviterUserId);
        List<JoinGroupNoticeData.NoticeUser> joinUsers = toNoticeUsers(userId, nameMap);
        NoticeData data = this.dataFactory.createJoinNoticeData(chatId, toNoticeUser(inviterUserId, nameMap), joinUsers);
        return persistGroupNotice(groupId,chatId, inviterUserId, data);
    }


    /**
     * 验证群和权限
     */
    private boolean validateGroupAndPermission(String groupId, String inviterUserId) {
        boolean isGroup = this.getMongoTemplate().exists(
            eq(where(col(GroupInfo::getId)).is(new ObjectId(groupId))),
            GroupInfo.class
        );

        if (!isGroup) {
            return false;
        }

        // 验证邀请人是否在群内
        boolean exists = this.getMongoTemplate().exists(
            eq(where(col(ChatSessionMember::getOwnerId)).is(new ObjectId(inviterUserId))
                .and(col(ChatSessionMember::getTargetId)).is(new ObjectId(groupId))),
            ChatSessionMember.class
        );

        return exists;
    }

    /**
     * 获取群会话
     */
    private ChatSession getGroupSession(ObjectId groupId) {
        ChatSessionMember anyMember = this.getMongoTemplate().findOne(
            eq(where(col(ChatSessionMember::getTargetId)).is(groupId)),
            ChatSessionMember.class
        );

        if (anyMember == null) {
            return null;
        }

        return this.getMongoTemplate().findOne(
            eq(where(col(ChatSession::getChatId)).is(anyMember.getChatId())),
            ChatSession.class
        );
    }

    private GroupMessageInfo createRemoveGroupNotice(ObjectId groupId,String chatId, List<String> removedUserId, String operationUserId) {
        Map<ObjectId, String> nameMap = queryNicknames(removedUserId, operationUserId);
        List<JoinGroupNoticeData.NoticeUser> removeUsers = toNoticeUsers(removedUserId, nameMap);

        NoticeData data = this.dataFactory.createRemoveGroupNoticeData(chatId, toNoticeUser(operationUserId, nameMap), removeUsers);
        return persistGroupNotice(groupId, chatId, operationUserId, data);
    }

    /** 批量查 userId + operatorId 的昵称，返回 id -> nickname。 */
    private Map<ObjectId, String> queryNicknames(List<String> userIds, String operatorId) {
        List<ObjectId> ids = userIds.stream().map(ObjectId::new).collect(Collectors.toList());
        ids.add(new ObjectId(operatorId));

        Query query = eq(where(col(UserInfo::getId)).in(ids));
        query.fields().include(col(UserInfo::getNickname));
        return this.getMongoTemplate().find(query, UserInfo.class).stream()
                .collect(Collectors.toMap(UserInfo::getId, UserInfo::getNickname));
    }

    private List<JoinGroupNoticeData.NoticeUser> toNoticeUsers(List<String> userIds, Map<ObjectId, String> nameMap) {
        return userIds.stream().map(id -> toNoticeUser(id, nameMap)).collect(Collectors.toList());
    }

    private JoinGroupNoticeData.NoticeUser toNoticeUser(String userId, Map<ObjectId, String> nameMap) {
        return new JoinGroupNoticeData.NoticeUser().setId(userId).setName(nameMap.get(new ObjectId(userId)));
    }

    /**
     * 落库一条群通知消息，并同步会话的 lastMsg* 摘要字段。
     *
     * @param groupId
     * @param data    具体的通知数据（JoinGroupNoticeData / RemoveGroupNoticeData 等 NoticeData 子类）
     */
    private GroupMessageInfo persistGroupNotice(ObjectId groupId, String chatId, String sndId, NoticeData data) {
        ObjectId id = new ObjectId();
        long seq = messageSeqAllocator.allocate(chatId, id.toHexString()).seq();


        GroupMessageInfo message = (GroupMessageInfo) new GroupMessageInfo().setId(id)
                .setSeq((int) seq)
                .setType(MessageType.NOTICE_MESSAGE.getType())
                .setChatId(chatId)
                .setSndId(new ObjectId(sndId))
                .setRcvId(groupId)
                .setState(0)
                .setData(new Gson().toJson(data))
                .setTimestamp(now());
        this.getMongoTemplate().insert(message);

        Query eq = eq(where(col(ChatSession::getChatId)).is(chatId));
        Update update = update().set(col(ChatSession::getLastMsgSeq), (int) seq)
                .set(col(ChatSession::getLastMsgType), MessageType.NOTICE_MESSAGE.getType())
                .set(col(ChatSession::getLastMsgSummary), new Gson().toJson(data))
                .set(col(ChatSession::getLastMsgTime), now());
        this.getMongoTemplate().updateFirst(eq, update, ChatSession.class);
        return message;
    }


    /**
     * 验证移除成员的权限
     */
    private ApiResult validateRemovePermission(LinkRemoveGroupMemberDto dto, List<ObjectId> removedUserIds) {
        // 查询操作人
        GroupMember operationMember = this.getMongoTemplate().findOne(
                eq(where(col(GroupMember::getGroupId)).is(new ObjectId(dto.getGroupId()))
                        .and(col(GroupMember::getUserId)).is(new ObjectId(dto.getOperationUserId()))),
                GroupMember.class
        );

        if (operationMember == null) {
            throw new RuntimeException("操作失败，操作人不在群聊中");
        }

        // 权限判断：必须是管理员或群主
        if (operationMember.getRole() != GroupRoleConstant.CREATOR
                && operationMember.getRole() != GroupRoleConstant.ADMINISTRATOR) {
            return ApiResult.error(GlobalCode.NO_PERMISSION);
        }

        // 查询待删除成员
        List<GroupMember> removeMembers = this.getMongoTemplate().find(
                eq(where(col(GroupMember::getGroupId)).is(new ObjectId(dto.getGroupId()))
                        .and(col(GroupMember::getUserId)).in(removedUserIds)),
                GroupMember.class
        );

        // 权限校验
        for (GroupMember removeMember : removeMembers) {
            // 不允许删除自己
            if (removeMember.getUserId().toHexString().equals(dto.getOperationUserId())) {
                return ApiResult.error("不能移除自己");
            }

            // 不允许删除群主
            if (removeMember.getRole() == GroupRoleConstant.CREATOR) {
                return ApiResult.error("不能移除群主");
            }

            // 管理员不能删除管理员
            if (operationMember.getRole() == GroupRoleConstant.ADMINISTRATOR
                    && removeMember.getRole() == GroupRoleConstant.ADMINISTRATOR) {
                return ApiResult.error("管理员不能移除其他管理员");
            }
        }

        return null; // 验证通过
    }

    /**
     * 执行移除群成员的事务操作
     */


    /**
     * 事务提交后：查询最新群成员并推送群通知
     */
    private void afterRemoveMemberCommit(GroupTxExecutorService.RemoveMemberContext context,GroupInfo group, LinkRemoveGroupMemberDto dto) {
        String chatId = context.getChatId();
//        Query query = eq(where(col(GroupMember::getGroupId)).is(group.getId()));
//        query.fields().include(col(GroupMember::getUserId));
//        List<String> memberIds = this.getPrimaryMongoTemplate().find(query, GroupMember.class).stream().map(v -> {
//            return v.getUserId().toHexString();
//        }).collect(Collectors.toList());
        Set<String> memberId = null;
        // 只给剩余成员推送群通知
        pushPublisher.push(
                EventType.GROUP_MESSAGE,
                memberId,
                createRemoveGroupNotice(new ObjectId(dto.getGroupId()), chatId, dto.getRemovedUserId(), dto.getOperationUserId())
        );
    }
}
