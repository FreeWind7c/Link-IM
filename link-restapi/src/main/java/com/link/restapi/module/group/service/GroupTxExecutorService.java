package com.link.restapi.module.group.service;

import com.link.base.entity.chat.ChatSession;
import com.link.base.entity.chat.ChatSessionMember;
import com.link.base.entity.group.GroupInfo;
import com.link.base.entity.group.GroupMember;
import com.link.base.manager.CacheDataManager;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.base.redis.BasePlatFormRedisService;
import com.link.common.constants.group.GroupRoleConstant;
import com.link.common.redis.RedisKeys;
import com.link.common.util.id.ChatIdGenerator;
import com.link.restapi.module.group.model.dto.LinkJoinGroupDto;
import com.link.restapi.module.group.model.dto.LinkRemoveGroupMemberDto;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月30日
 */
@Slf4j
@Component
public class GroupTxExecutorService extends BasePlatFormMongoService<GroupInfoService> {

    @Autowired
    private BasePlatFormRedisService redisService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheDataManager cacheManager;




    /**
     * 执行加入群聊的事务操作
     */
    @Transactional(rollbackFor = Exception.class)
    public GroupTxExecutorService.JoinGroupContext executeJoinGroupTx(LinkJoinGroupDto dto, ChatSession session) {

        // 1.创建群成员
        List<GroupMember> groupMembers = dto.getUserIds().stream().map(v -> {
            return new GroupMember().create(dto.getGroupId(), v,
                    dto.getInviterUserId(), GroupRoleConstant.REGULAR_MEMBER);
        }).collect(Collectors.toList());

        // 2.创建会话成员，并处理 blackoutGaps
        int currentSeq = getCurrentSeq(session.getChatId());
        List<ChatSessionMember> sessionMembers = new java.util.ArrayList<>();

        for (GroupMember groupMember : groupMembers) {
            String userId = groupMember.getUserId().toHexString();

            // 查询是否已存在该用户的会话成员记录（被踢过又拉回的情况）
            ChatSessionMember existingMember = this.getPrimaryMongoTemplate().findOne(
                eq(where(col(ChatSessionMember::getOwnerId)).is(new ObjectId(userId))
                    .and(col(ChatSessionMember::getChatId)).is(session.getChatId())),
                ChatSessionMember.class
            );

            ChatSessionMember sessionMember;
            List<ChatSessionMember.Gap> gaps;

            if (existingMember != null) {
                // 重新加入：使用已有记录
                sessionMember = existingMember;
                gaps = sessionMember.getBlackoutGaps();

                if (gaps != null && !gaps.isEmpty()) {
                    // 封闭最后一个开口的 gap
                    ChatSessionMember.Gap lastGap = gaps.get(gaps.size() - 1);
                    if (lastGap.getTo() == null) {
                        lastGap.setTo(currentSeq);
                    }
                }
            } else {
                // 首次加入：创建新记录
                sessionMember = new ChatSessionMember().createGroup(userId, dto.getGroupId(), session.getChatId());

                if (currentSeq > 0) {
                    // 添加初始 gap [1, currentSeq]，表示加入前的消息不可见
                    gaps = new java.util.ArrayList<>();
                    gaps.add(new ChatSessionMember.Gap(1, currentSeq));
                    sessionMember.setBlackoutGaps(gaps);
                }
            }

            sessionMembers.add(sessionMember);
        }

        // 3.添加群成员与会话成员
        this.getMongoTemplate().insert(groupMembers,GroupMember.class);
        BulkOperations bulkOps = this.getMongoTemplate().bulkOps(
                BulkOperations.BulkMode.UNORDERED,
                ChatSessionMember.class
        );

        
        

        for (ChatSessionMember sessionMember : sessionMembers) {
            Query query = eq(where(col(ChatSessionMember::getOwnerId)).is(sessionMember.getOwnerId())
                    .and(col(ChatSessionMember::getChatId)).is(session.getChatId()));
            Update update = update()
                    .set(col(ChatSessionMember::isActive), true)
                    .set(col(ChatSessionMember::getBlackoutGaps), sessionMember.getBlackoutGaps())
                    .setOnInsert("owner_id", sessionMember.getOwnerId())
                    .setOnInsert("chat_id", sessionMember.getChatId())
                    .setOnInsert("target_id", sessionMember.getTargetId())
                    .setOnInsert("type", sessionMember.getType())
                    .setOnInsert("last_read_seq", sessionMember.getLastReadSeq())
                    .setOnInsert("at_list", sessionMember.getAtList())
                    .setOnInsert("show_top", sessionMember.isShowTop())
                    .setOnInsert("silence", sessionMember.isSilence())
                    .setOnInsert("hidden", sessionMember.isHidden())
                    .setOnInsert("join_time", sessionMember.getJoinTime());

            bulkOps.upsert(query, update);
        }
        bulkOps.execute();

        // 4.更新群成员数量
        this.getMongoTemplate().updateFirst(
                eq(where(col(GroupInfo::getId)).is(new ObjectId(dto.getGroupId()))),
                update().inc(col(GroupInfo::getGroupMemberSize),dto.getUserIds().size()),
                GroupInfo.class
        );

        return new GroupTxExecutorService.JoinGroupContext(groupMembers.size(), session , sessionMembers);
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupTxExecutorService.RemoveMemberContext executeRemoveMemberTx(LinkRemoveGroupMemberDto dto, List<ObjectId> removedUserIds) {
        // 查询待删除成员
        Query removeQuery = eq(
                where(col(GroupMember::getGroupId)).is(new ObjectId(dto.getGroupId()))
                        .and(col(GroupMember::getUserId)).in(removedUserIds)
        );

        List<GroupMember> removeMembers = this.getPrimaryMongoTemplate().find(removeQuery, GroupMember.class);
        if (removeMembers.isEmpty()) {
            return new GroupTxExecutorService.RemoveMemberContext(0, null);
        }

        String chatId = ChatIdGenerator.nextId(dto.getGroupId());
        int currentSeq = getCurrentSeq(chatId);
        int gapFrom = currentSeq + 1;

        // 更新会话成员状态（软删除）
        Query updateQuery = eq(
                where(col(ChatSessionMember::getTargetId)).is(new ObjectId(dto.getGroupId()))
                        .and(col(ChatSessionMember::getOwnerId)).in(removedUserIds)
        );

        Update update = update()
                .set(col(ChatSessionMember::isActive), false)
                .push(col(ChatSessionMember::getBlackoutGaps), new ChatSessionMember.Gap(gapFrom, null));

        this.getMongoTemplate().updateMulti(updateQuery, update, ChatSessionMember.class);

        // 删除群成员记录
        this.getMongoTemplate().remove(removeQuery, GroupMember.class);

        // 更新群成员数量
        this.getMongoTemplate().updateFirst(
                eq(where(colOf(GroupInfo::getId)).is(new ObjectId(dto.getGroupId()))),
                update().inc(colOf(GroupInfo::getGroupMemberSize), -removeMembers.size()),
                GroupInfo.class
        );



        return new GroupTxExecutorService.RemoveMemberContext(removeMembers.size(), chatId);
    }

    /**
     * 查询已存在的群成员
     */
    private List<GroupMember> findExistingGroupMembers(ObjectId groupId, List<String> userIds) {
        return this.getPrimaryMongoTemplate().find(
                eq(where(col(GroupMember::getGroupId)).is(groupId)
                        .and(col(GroupMember::getUserId)).in(
                                userIds.stream().map(ObjectId::new).collect(Collectors.toList())
                        )),
                GroupMember.class
        );
    }

    /**
     * 处理会话成员（新增或复活）
     */
    private ChatSessionMember processSessionMember(ObjectId userId, String chatId, String groupId) {
        ChatSessionMember existMember = this.getPrimaryMongoTemplate().findOne(
                eq(where(col(ChatSessionMember::getOwnerId)).is(userId)
                        .and(col(ChatSessionMember::getChatId)).is(chatId)),
                ChatSessionMember.class
        );

        if (existMember != null) {
            // 复活旧成员
            int currentSeq = getCurrentSeq(chatId);
            updateBlackoutGaps(existMember, currentSeq);

            this.getMongoTemplate().updateFirst(
                    eq(where(col(ChatSessionMember::getOwnerId)).is(userId)
                            .and(col(ChatSessionMember::getChatId)).is(chatId)),
                    update()
                            .set(col(ChatSessionMember::isActive), true)
                            .set(col(ChatSessionMember::getBlackoutGaps), existMember.getBlackoutGaps())
                            .set(col(ChatSessionMember::getLastReadSeq), currentSeq),
                    ChatSessionMember.class
            );

            existMember.setActive(true);
            existMember.setLastReadSeq(currentSeq);
            return existMember;
        } else {
            // 新成员
            return new ChatSessionMember().createGroup(userId.toHexString(), groupId, chatId);
        }
    }

    /**
     * 更新黑名单间隙
     */
    private void updateBlackoutGaps(ChatSessionMember member, int currentSeq) {
        List<ChatSessionMember.Gap> gaps = member.getBlackoutGaps();
        if (gaps == null) {
            return;
        }

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

    private int getCurrentSeq(String chatId) {
        String seqValue = stringRedisTemplate.opsForValue().get(RedisKeys.SEQ + chatId);
        if (seqValue == null || seqValue.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(seqValue);
        } catch (NumberFormatException e) {
            log.warn("Invalid seq value for chatId {}: {}", chatId, seqValue);
            return 0;
        }
    }

    /**
     * 移除群成员的上下文信息
     */
    public static class RemoveMemberContext {
        private final int removedCount;
        private final String chatId;

        public RemoveMemberContext(int removedCount, String chatId) {
            this.removedCount = removedCount;
            this.chatId = chatId;
        }

        public int getRemovedCount() { return removedCount; }
        public String getChatId() { return chatId; }
    }

    /**
     * 加入群聊的上下文信息
     */
    public static class JoinGroupContext {
        private final int newMembersCount;
        private final ChatSession session;
        private final List<ChatSessionMember> pushMembers;

        public JoinGroupContext(int newMembersCount, ChatSession session, List<ChatSessionMember> pushMembers) {
            this.newMembersCount = newMembersCount;
            this.session = session;
            this.pushMembers = pushMembers;
        }

        public int getNewMembersCount() { return newMembersCount; }
        public ChatSession getSession() { return session; }
        public List<ChatSessionMember> getPushMembers() { return pushMembers; }
    }


}
