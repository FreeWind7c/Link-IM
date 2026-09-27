package com.link.restapi.module.friend.service;

import com.alibaba.fastjson.TypeReference;
import com.link.base.entity.data.NoticeData;
import com.link.base.entity.friend.FriendInfo;
import com.link.base.entity.friend.FriendRequest;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.facotry.LinkBaseDataFactory;
import com.link.base.facotry.LinkMessageFactory;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.base.repository.ChatSessionRepository;
import com.link.base.vo.DefaultMessageVO;
import com.link.common.constants.friend.LinkFriendRequestStatus;
import com.link.common.constants.friend.LinkFriendStatus;
import com.link.common.constants.group.LinkNoticeTemplate;
import com.link.restapi.module.chat.model.dto.LinkCreateChatDto;
import com.link.restapi.module.chat.service.ChatSessionService;
import com.link.restapi.module.user.model.dto.LinkApproveFriendDTO;
import com.link.restapi.utils.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月30日
 */
@Slf4j
@Component
public class FriendTxExecutorService extends BasePlatFormMongoService<FriendInfo> {

    @Autowired
    private LinkBaseDataFactory dataFactory;

    @Autowired
    private LinkMessageFactory messageFactory;

    @Autowired
    private ChatSessionService sessionService;

    @Autowired
    private ChatSessionRepository sessionRepository;

    @Transactional(rollbackFor = Exception.class)
    public DefaultMessageVO approveTx(LinkApproveFriendDTO dto, ObjectId userId, ObjectId friendId, FriendRequest request) {
        establishFriendship(userId, friendId, request);
        DefaultMessageVO welcomeMessage = createChatAndWelcomeMessage(dto, userId, friendId);
        return welcomeMessage;
    }

    /**
     * 建立好友关系（新建或恢复）
     */
    private void establishFriendship(ObjectId userId, ObjectId friendId, FriendRequest request) {
        FriendInfo existed = this.getPrimaryMongoTemplate().findOne(eq(
                where(col(FriendInfo::getUserId)).is(userId)
                        .and(col(FriendInfo::getFriendId)).is(friendId)),FriendInfo.class);

        long timestamp = now();

        if (existed != null) {
            // 之前被删除/拉黑过，对称恢复两条记录
            restoreFriendship(userId, friendId, timestamp);
        } else {
            // 创建新的好友关系
            createNewFriendship(userId, friendId, request, timestamp);
        }

        this.getMongoTemplate().updateFirst(
                eq(where(col(FriendInfo::getId)).is(request.getId())),
                update().set(col(FriendRequest::getStatus), LinkFriendRequestStatus.APPROVED),
                FriendRequest.class
        );
    }

    /**
     * 创建聊天会话并发送欢迎消息
     */
    private DefaultMessageVO createChatAndWelcomeMessage(LinkApproveFriendDTO dto, ObjectId userId, ObjectId friendId) {
        // 创建聊天会话
        ApiResult result = sessionService.createChat(
                new LinkCreateChatDto()
                        .setUserId(dto.getUserId())
                        .setTargetId(dto.getFriendId()));
        String chatId = result.getData(new TypeReference<String>() {});

        // 创建欢迎消息
        NoticeData noticeData = this.dataFactory.createDefaultNoticeData(chatId, LinkNoticeTemplate.FRIEND_WELCOME_MESSAGE);
        DefaultMessageInfo message = (DefaultMessageInfo) this.messageFactory.create(
                chatId, userId, friendId,
                noticeData.getMessageType(), noticeData, DefaultMessageInfo.class);

        // 更新会话
        this.sessionRepository.updateSession(message);

        return message.toVo();
    }

    /**
     * 恢复好友关系
     */
    private void restoreFriendship(ObjectId userId, ObjectId friendId, long timestamp) {
        Query both = eq(where(col(FriendInfo::getUserId)).in(userId, friendId)
                .and(col(FriendInfo::getFriendId)).in(userId, friendId));
        this.updateMulti(both, new Update()
                .set(col(FriendInfo::getStatus), LinkFriendStatus.F)
                .set(col(FriendInfo::getUpdatedTime), timestamp));
    }

    /**
     * 创建新的好友关系
     */
    private void createNewFriendship(ObjectId userId, ObjectId friendId, FriendRequest request, long timestamp) {
        FriendInfo userFriend = new FriendInfo()
                .create(userId.toHexString(), friendId.toHexString(), request.getSource());
        userFriend.setCreatedTime(timestamp);
        userFriend.setUpdatedTime(timestamp);

        FriendInfo friendUser = new FriendInfo()
                .create(friendId.toHexString(), userId.toHexString(), request.getSource());
        friendUser.setCreatedTime(timestamp);
        friendUser.setUpdatedTime(timestamp);

        this.getMongoTemplate().insert(List.of(userFriend, friendUser), FriendInfo.class);
    }

}



