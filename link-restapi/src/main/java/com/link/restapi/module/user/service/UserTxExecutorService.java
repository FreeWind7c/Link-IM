package com.link.restapi.module.user.service;

import com.link.base.entity.chat.ChatSession;
import com.link.base.entity.chat.ChatSessionMember;
import com.link.base.entity.friend.FriendInfo;
import com.link.base.entity.user.UserInfo;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.common.constants.friend.LinkFriendSource;
import com.link.common.constants.session.ChatSessionCategoryKeys;
import com.link.common.constants.user.UserCategoryKeys;
import com.link.common.util.id.ChatIdGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月30日
 */
@Slf4j
@Component
public class UserTxExecutorService extends BasePlatFormMongoService<UserInfo> {

    @Transactional(rollbackFor = Exception.class)
    public UserTxExecutorService.RegisterContext registerTx(UserInfo linkUser) {
        this.insert(linkUser);
        // 创建机器人好友与会话
        UserInfo boot = this.getPrimaryMongoTemplate().findOne(eq(where(col(UserInfo::getCategory)).is(UserCategoryKeys.BOT)),UserInfo.class);
        createFriend(linkUser,boot);
        String chatId = createChat(linkUser, boot);
        return new UserTxExecutorService.RegisterContext(chatId,boot);
    }




    /**
     * 在两个普通用户之间建立好友关系与单聊会话（初始化数据用）。
     *
     * @return 会话 chatId，供调用方生成欢迎消息
     */
    @Transactional(rollbackFor = Exception.class)
    public String bindFriendTx(UserInfo user, UserInfo friend) {
        long timestamp = now();
        createFriendPair(user, friend, timestamp);
        return createUserChat(user, friend, timestamp);
    }

    private void createFriendPair(UserInfo user, UserInfo friend, long timestamp) {
        FriendInfo f1 = new FriendInfo().create(user.getId().toHexString(), friend.getId().toHexString(), LinkFriendSource.SYS);
        FriendInfo f2 = new FriendInfo().create(friend.getId().toHexString(), user.getId().toHexString(), LinkFriendSource.SYS);
        for (FriendInfo f : List.of(f1, f2)) {
            f.setCreatedTime(timestamp);
            f.setUpdatedTime(timestamp);
        }
        this.getMongoTemplate().insert(List.of(f1, f2), FriendInfo.class);
    }

    private String createUserChat(UserInfo user, UserInfo friend, long timestamp) {
        String chatId = ChatIdGenerator.nextId(user.getId().toHexString(), friend.getId().toHexString());
        ChatSession session = new ChatSession().createSingle(chatId, ChatSessionCategoryKeys.USER);
        session.setCreatedTime(timestamp);
        session.setUpdatedTime(timestamp);

        ChatSessionMember s1 = new ChatSessionMember().createSingle(user.getId().toHexString(), friend.getId().toHexString(), chatId);
        ChatSessionMember s2 = new ChatSessionMember().createSingle(friend.getId().toHexString(), user.getId().toHexString(), chatId);
        for (ChatSessionMember member : List.of(s1, s2)) {
            member.setJoinTime(timestamp);
            member.setCreatedTime(timestamp);
            member.setUpdatedTime(timestamp);
        }

        this.getMongoTemplate().insert(session);
        this.getMongoTemplate().insert(List.of(s1, s2), ChatSessionMember.class);
        return chatId;
    }

    private String createChat(UserInfo linkUser, UserInfo boot) {
        ChatSession session = new ChatSession().createSingle(ChatIdGenerator.nextId(linkUser.getId().toHexString(), boot.getId().toHexString()), ChatSessionCategoryKeys.BOOT);
        ChatSessionMember s1 = new ChatSessionMember().createSingle(linkUser.getId().toHexString(), boot.getId().toHexString(), session.getChatId());
        ChatSessionMember s2 = new ChatSessionMember().createSingle(boot.getId().toHexString(), linkUser.getId().toHexString(),  session.getChatId());
        this.getMongoTemplate().insert(session);
        this.getMongoTemplate().insert(List.of(s1,s2),ChatSessionMember.class);
        System.out.println("3");
        return session.getChatId();
    }

    private void createFriend(UserInfo linkUser, UserInfo boot) {
        FriendInfo f1 = new FriendInfo().create(linkUser.getId().toHexString(), boot.getId().toHexString(), LinkFriendSource.SYS);
        FriendInfo f2 = new FriendInfo().create( boot.getId().toHexString(), linkUser.getId().toHexString(), LinkFriendSource.SYS);
        this.getMongoTemplate().insert(List.of(f1, f2),FriendInfo.class);
    }


    @Data
    @AllArgsConstructor
    public static class RegisterContext {
        private String chatId;

        private UserInfo bot;
    }
}
