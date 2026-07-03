package com.link.restapi.group.service;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.google.gson.Gson;
import com.link.common.core.event.EventType;
import com.link.common.core.model.group.LinkJoinGroup;
import com.link.common.core.model.group.LinkRemoveGroup;
import com.link.common.im.data.LinkChatSession;
import com.link.common.redis.RedisConstant;
import com.link.common.util.id.ChatIdGenerator;
import com.link.common.util.id.LinkID;
import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.constants.group.GroupRoleConstant;
import com.link.im.entity.chat.ChatSessionMember;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.data.NoticeData;
import com.link.im.entity.data.notice.JoinGroupNoticeData;
import com.link.im.entity.data.notice.RemoveGroupNoticeData;
import com.link.im.entity.group.GroupInfo;
import com.link.im.entity.group.GroupMember;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.entity.message.type.MessageType;
import com.link.im.entity.user.UserInfo;
import com.link.im.enums.gloabl.GlobalCode;
import com.link.im.enums.group.GroupInfoCode;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.service.LinkRedisService;
import com.link.im.util.ApiResult;
import com.link.restapi.group.model.dto.LinkAddGroupAdministratorDto;
import com.link.restapi.group.model.dto.LinkCreateGroupDto;
import com.link.restapi.group.model.dto.LinkJoinGroupDto;
import com.link.restapi.group.model.dto.LinkRemoveGroupMemberDto;
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
    private LinkRedisService redisService;

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
        // 待新增的会话成员
        List<ChatSessionMember> newChatSessionMembers = new ArrayList<>();
        // 用于推送的会话成员（新增 + 复活）
        List<ChatSessionMember> pushChatSessionMembers = new ArrayList<>();

        for (String userId : dto.getUserIds()) {

            ObjectId uid = new ObjectId(userId);
            if (existUserIds.contains(uid)) {
                continue;
            }

            groupMembers.add(new GroupMember().create(group.getId().toHexString(),userId,dto.getInviterUserId(),GroupRoleConstant.REGULAR_MEMBER));

            ChatSessionMember existSessionMember = this.getMongoTemplate().findOne(
                    eq(where(col(ChatSessionMember::getOwnerId)).is(uid)
                            .and(col(ChatSessionMember::getChatId)).is(session.getChatId())),
                    ChatSessionMember.class
            );

            if (existSessionMember != null) {
                String seqStr = stringRedisTemplate.opsForValue().get(RedisConstant.SEQ + session.getChatId());
                int currentSeq = (seqStr != null && !seqStr.isEmpty()) ? Integer.parseInt(seqStr) : 0;
                List<ChatSessionMember.Gap> gaps = existSessionMember.getBlackoutGaps();
                if (gaps != null) {
                    for (int i = gaps.size() - 1; i >= 0; i--) {
                        ChatSessionMember.Gap gap = gaps.get(i);
                        if (gap.getTo() == null) {
                            if (gap.getFrom() > currentSeq) {
                                gaps.remove(i);
                            } else {
                                gap.setTo(currentSeq);
                            }
                            break;
                        }
                    }
                }

                this.getMongoTemplate().updateFirst(
                        eq(where(col(ChatSessionMember::getOwnerId)).is(uid)
                                .and(col(ChatSessionMember::getChatId)).is(session.getChatId())),
                        update().set(col(ChatSessionMember::isActive), true)
                                .set(col(ChatSessionMember::getBlackoutGaps), gaps)
                                // 未读数 = lastMsgSeq - lastReadSeq，被踢期间的消息已被空档过滤、拉不到，
                                // 不抬 lastReadSeq 会把这些拉不到的消息算成未读。复活即视为读到当前最新，未读归零。
                                .set(col(ChatSessionMember::getLastReadSeq), currentSeq),
                        ChatSessionMember.class
                );
                existSessionMember.setActive(true);
                existSessionMember.setLastReadSeq(currentSeq);
                pushChatSessionMembers.add(existSessionMember);
            }
            else {
                ChatSessionMember newMember = new ChatSessionMember()
                        .createGroup(userId, group.getId().toHexString(), session.getChatId());
                newChatSessionMembers.add(newMember);
                pushChatSessionMembers.add(newMember);
            }
        }

        if (groupMembers.isEmpty()) {
            return ApiResult.success();
        }

        this.getMongoTemplate().insert(groupMembers, GroupMember.class);
        if (!newChatSessionMembers.isEmpty()) {
            this.getMongoTemplate().insert(newChatSessionMembers, ChatSessionMember.class);
        }

        // 更新成员数量
        this.updateFirst(
                eq(where(col(GroupInfo::getId)).is(group.getId())),
                update().inc(col(GroupInfo::getGroupMemberSize), groupMembers.size())
        );

        // 失效群成员缓存，下一条群消息由 LinkGroupMessageProcessor 懒加载重建
        redisTemplate.delete(RedisConstant.CHAT_SESSION_MEMBER  + session.getChatId());

        for (ChatSessionMember member : pushChatSessionMembers) {
            LinkJoinGroup payload = new LinkJoinGroup().setSession(toLinkChatSession(session, member, group));
            this.pushPublisher.push( EventType.JOIN_GROUP,Arrays.asList(member.getOwnerId().toHexString()), payload);
        }
        Set<String> memberIds = redisService.getChatMemberIds(session.getChatId());
        this.pushPublisher.push(EventType.GROUP_MESSAGE,memberIds,
                createJoinGroupNotice(session.getChatId(),dto.getUserIds(),dto.getInviterUserId()));
        return ApiResult.success();
    }

    private GroupMessageInfo createJoinGroupNotice(String chatId, List<String> userId, String inviterUserId) {
        Map<ObjectId, String> nameMap = queryNicknames(userId, inviterUserId);
        List<JoinGroupNoticeData.NoticeUser> joinUsers = toNoticeUsers(userId, nameMap);

        JoinGroupNoticeData data = new JoinGroupNoticeData();
        data.setInviter(toNoticeUser(inviterUserId, nameMap));
        data.setJoinUsers(joinUsers);

        return persistGroupNotice(chatId, inviterUserId, data);
    }

    private GroupMessageInfo createRemoveGroupNotice(String chatId, List<String> removedUserId, String operationUserId) {
        Map<ObjectId, String> nameMap = queryNicknames(removedUserId, operationUserId);
        List<JoinGroupNoticeData.NoticeUser> removeUsers = toNoticeUsers(removedUserId, nameMap);

        RemoveGroupNoticeData data = new RemoveGroupNoticeData();
        data.setOperationUser(toNoticeUser(operationUserId, nameMap));
        data.setRemoveUsers(removeUsers);

        return persistGroupNotice(chatId, operationUserId, data);
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
     * @param data 具体的通知数据（JoinGroupNoticeData / RemoveGroupNoticeData 等 NoticeData 子类）
     */
    private GroupMessageInfo persistGroupNotice(String chatId, String sndId, NoticeData data) {
        String id = LinkID.nextIdStr();
        long seq = messageSeqAllocator.allocate(chatId, id).seq();

        NoticeData noticeData = new NoticeData().setChatId(chatId).setData(data);
        GroupMessageInfo message = (GroupMessageInfo) new GroupMessageInfo().setId(id)
                .setSeq((int) seq)
                .setType(MessageType.NOTICE_MESSAGE.getType())
                .setChatId(chatId)
                .setSndId(sndId)
                .setRcvId(chatId)
                .setState(0)
                .setData(noticeData)
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

    public ApiResult removeMember(LinkRemoveGroupMemberDto dto) {

        // 参数校验
        if (!stringValidator(dto.getGroupId(), dto.getOperationUserId())
                || CollectionUtils.isEmpty(dto.getRemovedUserId())) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        // 校验ObjectId是否合法
        if (!ObjectId.isValid(dto.getGroupId())) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        List<ObjectId> removedUserIds = dto.getRemovedUserId().stream()
                .filter(StringUtils::hasText)
                .distinct()
                .map(item -> {return new ObjectId(item);})
                .toList();

        if (removedUserIds.isEmpty()) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        // 查询操作人
        GroupMember operationMember = this.getMongoTemplate().findOne(
                eq(where(col(GroupMember::getGroupId)).is(new ObjectId(dto.getGroupId()))
                        .and(col(GroupMember::getUserId)).is(new ObjectId(dto.getOperationUserId())))
                ,GroupMember.class
        );

        if (operationMember == null) {
            throw new RuntimeException("操作失败，操作人不在群聊中");
        }

        // 权限判断
        if (operationMember.getRole() != GroupRoleConstant.CREATOR
                && operationMember.getRole() != GroupRoleConstant.ADMINISTRATOR) {
            return ApiResult.error(GlobalCode.NO_PERMISSION);
        }

        // 查询所有待删除成员
        Query query = eq(
                where(col(GroupMember::getGroupId)).is(new ObjectId(dto.getGroupId()))
                        .and(col(GroupMember::getUserId)).in(removedUserIds)
        );

        List<GroupMember> removeMembers = this.getMongoTemplate().find(query,GroupMember.class);

        if (removeMembers.isEmpty()) {
            return ApiResult.success();
        }

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

        Query removeQuery = eq(
                where(col(GroupMember::getGroupId)).is(new ObjectId(dto.getGroupId()))
                        .and(col(GroupMember::getUserId)).in(removedUserIds)
        );

        Query updateQuery = eq(
                where(col(ChatSessionMember::getTargetId)).is(new ObjectId(dto.getGroupId()))
                        .and(col(ChatSessionMember::getOwnerId)).in(removedUserIds)
        );

        String chatId = ChatIdGenerator.nextId(dto.getGroupId());
        String seqStr = stringRedisTemplate.opsForValue().get(RedisConstant.SEQ + chatId);
        int currentSeq = StringUtils.hasText(seqStr) ? Integer.parseInt(seqStr) : 0;
        int gapFrom = currentSeq + 1;

        Update update = update()
                .set(col(ChatSessionMember::isActive), false)
                .push(col(ChatSessionMember::getBlackoutGaps), new ChatSessionMember.Gap(gapFrom, null));

        this.getMongoTemplate().updateMulti(updateQuery,update,ChatSessionMember.class);
        this.getMongoTemplate().remove(removeQuery,GroupMember.class);

        // 群成员数 -N（按实际删除条数，不用请求里的 id 数，避免重复 / 不存在的 id 把计数减多）
        this.getMongoTemplate().updateFirst(
                eq(where(colOf(GroupInfo::getId)).is(new ObjectId(dto.getGroupId()))),
                update().inc(colOf(GroupInfo::getGroupMemberSize), -removeMembers.size()),
                GroupInfo.class
        );

        redisTemplate.delete(RedisConstant.CHAT_SESSION_MEMBER + chatId);

        this.pushPublisher.push(EventType.REMOVE_GROUP_MEMBER,dto.getRemovedUserId(), new LinkRemoveGroup(chatId));

        // 被移除成员已退群，只给剩余成员推群通知（getChatMemberIds 取的是删除后的成员表）
        Set<String> memberIds = redisService.getChatMemberIds(chatId);
        this.pushPublisher.push(EventType.GROUP_MESSAGE, memberIds, createRemoveGroupNotice(chatId, dto.getRemovedUserId(), dto.getOperationUserId()));
        return ApiResult.success();
    }
}
