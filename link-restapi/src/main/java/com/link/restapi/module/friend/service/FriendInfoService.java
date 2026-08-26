package com.link.restapi.module.friend.service;

import com.alibaba.fastjson.TypeReference;
import com.link.common.constants.friend.LinkFriendRequestStatus;
import com.link.common.core.event.EventType;

import com.link.common.core.model.friend.LinkApproveFriend;
import com.link.common.core.model.friend.LinkFriend;
import com.link.common.constants.group.LinkNoticeTemplate;
import com.link.common.pager.Pager;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.data.NoticeData;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.restapi.enums.friend.FriendApiCode;
import com.link.common.constants.friend.LinkFriendStatus;
import com.link.im.factory.LinkMessageFactory;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.entity.friend.FriendInfo;
import com.link.im.entity.friend.FriendRequest;
import com.link.im.entity.user.UserInfo;
import com.link.im.factory.LinkBaseDataFactory;
import com.link.im.repository.ChatSessionRepository;
import com.link.im.vo.DefaultMessageVO;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.restapi.module.chat.model.dto.LinkCreateChatDto;
import com.link.restapi.module.chat.service.ChatSessionService;
import com.link.restapi.module.friend.model.dto.LinkAddFriendDTO;
import com.link.restapi.module.friend.model.dto.LinkFriendRequestDTO;
import com.link.restapi.module.friend.model.dto.LinkMyFriendDTO;
import com.link.restapi.module.friend.model.vo.LinkFriendInfoVO;
import com.link.common.pager.PageRequest;
import com.link.restapi.module.friend.model.vo.LinkFriendRequestVO;
import com.link.restapi.push.RemotePushPublisher;
import com.link.restapi.module.user.model.dto.LinkApproveFriendDTO;

import com.link.restapi.utils.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.redisson.liveobject.condition.EQCondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 */
@Slf4j
@Component
public class FriendInfoService extends BasePlatFormMongoService<FriendInfo> {

    @Autowired
    private RemotePushPublisher pushPublisher;

    @Autowired
    private LinkBaseDataFactory dataFactory;

    @Autowired
    private LinkMessageFactory messageFactory;

    @Autowired
    private ChatSessionService sessionService;

    @Autowired
    private ChatSessionRepository sessionRepository;

    public ApiResult addFriend(LinkAddFriendDTO dto) {
        // 1. 验证
        ApiResult validationResult = validateAddFriend(dto);
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        // 2. 创建好友申请记录
        String id = createFriendRequest(dto);

        // 3. 推送好友申请通知
        pushAddFriendNotification(dto,id);

        return ApiResult.success(FriendApiCode.NOTIFY_USER);
    }

    /**
     * 验证添加好友请求
     */
    private ApiResult validateAddFriend(LinkAddFriendDTO dto) {
        ObjectId userId = new ObjectId(dto.getUserId());
        ObjectId friendId = new ObjectId(dto.getFriendId());

        // 验证目标用户是否存在
        if (!userExists(friendId)) {
            return ApiResult.error(FriendApiCode.FRIEND_DOES_NOT_EXIST);
        }

        // 验证好友关系是否已存在
        if (friendshipExists(userId, friendId)) {
            return ApiResult.error(FriendApiCode.FRIEND_EXIST);
        }

        return ApiResult.success();
    }

    /**
     * 创建好友申请记录
     */
    private String createFriendRequest(LinkAddFriendDTO dto) {
        long timestamp = now();
        ObjectId id = new ObjectId();
        FriendRequest request = new FriendRequest()
                .setUserId(new ObjectId(dto.getUserId()))
                .setFriendId(new ObjectId(dto.getFriendId()))
                .setSource(dto.getSource())
                .setStatus(0);
        request.setId(id);
        request.setCreatedTime(timestamp);
        request.setUpdatedTime(timestamp);
        this.getMongoTemplate().insert(request);
        return id.toHexString();
    }

    /**
     * 推送添加好友通知
     */
    private void pushAddFriendNotification(LinkAddFriendDTO dto, String id) {
        UserInfo user = this.getMongoTemplate().findById(new ObjectId(dto.getUserId()), UserInfo.class);
        LinkFriend linkFriend = new LinkFriend()
                .setRequestId(id)
                .setUserId(user.getId().toHexString())
                .setNickname(user.getNickname())
                .setAvatar(user.getAvatar());
        this.pushPublisher.push(EventType.ADD_FRIEND, Arrays.asList(dto.getFriendId()), linkFriend);
    }

    // ==================== 公共方法 ====================

    /**
     * 验证用户是否存在
     */
    private boolean userExists(ObjectId userId) {
        return this.getMongoTemplate().exists(
                new Query(Criteria.where(col(UserInfo::getId)).is(userId)),
                UserInfo.class);
    }

    /**
     * 验证好友关系是否存在
     */
    private boolean friendshipExists(ObjectId userId, ObjectId friendId) {
        return this.exists(new Query(Criteria
                .where(col(FriendInfo::getUserId)).is(userId)
                .and(col(FriendInfo::getFriendId)).is(friendId)));
    }
    public ApiResult queryFriend(String userId) {
        // 查询出该用户的好友
        Query eq1 = eq(
                where(col(FriendInfo::getUserId)).is(new ObjectId(userId))
                .and(col(FriendInfo::getStatus)).is(LinkFriendStatus.F));
        Map<ObjectId, FriendInfo> friendMap = this.find(eq1).stream().collect(Collectors.toMap(
                        FriendInfo::getFriendId,
                        Function.identity()
                ));

        // 根据好友ID查询好友详细信息
        Query eq2 = eq(where(col(FriendInfo::getId)).in(friendMap.keySet()));
        eq2.fields().include(col(UserInfo::getId),col(UserInfo::getNickname),col(UserInfo::getAvatar));
        List<UserInfo> userInfos = this.getMongoTemplate().find(eq2, UserInfo.class);

        // TODO 构建好友VO类
        List<LinkFriendInfoVO> friends = userInfos.stream().map(item -> {
            FriendInfo friendInfo = friendMap.get(item.getId());
            return LinkFriendInfoVO.fromVo(friendInfo, item);
        }).collect(Collectors.toList());


        return ApiResult.success().setData(friends);
    }

    @Transactional
    public ApiResult approvePetition(LinkApproveFriendDTO dto) {
        // 1. 验证
        ApiResult validationResult = validateApproveFriend(dto);
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        ObjectId userId = new ObjectId(dto.getUserId());
        ObjectId friendId = new ObjectId(dto.getFriendId());

        // 2. 查询好友请求
        FriendRequest request = findFriendRequest(dto.getId());

        // 3. 建立好友关系
        establishFriendship(userId, friendId, request);

        // 4. 创建会话并发送欢迎消息
        DefaultMessageVO welcomeMessage = createChatAndWelcomeMessage(dto, userId, friendId);

        // 5. 推送通知
        pushApprovalNotifications(dto, welcomeMessage);

        return ApiResult.success();
    }

    /**
     * 验证好友申请
     */
    private ApiResult validateApproveFriend(LinkApproveFriendDTO dto) {
        if (dto.getUserId().equals(dto.getFriendId())) {
            return ApiResult.error(FriendApiCode.DONT_ADD_SELF);
        }

        ObjectId userId = new ObjectId(dto.getUserId());
        ObjectId friendId = new ObjectId(dto.getFriendId());

        // 验证好友关系是否已经建立（状态为正常）
        FriendInfo existed = this.findOne(eq(
                where(col(FriendInfo::getUserId)).is(userId)
                        .and(col(FriendInfo::getFriendId)).is(friendId)));

        if (existed != null && existed.getStatus() == LinkFriendStatus.F) {
            return ApiResult.error(FriendApiCode.FRIEND_EXIST);
        }

        return ApiResult.success();
    }

    /**
     * 查询好友请求
     */
    private FriendRequest findFriendRequest(String id) {
        return this.getMongoTemplate().findOne(eq(
                where(col(FriendRequest::getId)).is(new ObjectId(id))
        ), FriendRequest.class);
    }

    /**
     * 建立好友关系（新建或恢复）
     */
    private void establishFriendship(ObjectId userId, ObjectId friendId, FriendRequest request) {
        FriendInfo existed = this.findOne(eq(
                where(col(FriendInfo::getUserId)).is(userId)
                        .and(col(FriendInfo::getFriendId)).is(friendId)));

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
     * 推送好友申请通过的通知
     */
    private void pushApprovalNotifications(LinkApproveFriendDTO dto, DefaultMessageVO welcomeMessage) {
        // 存储消息
        this.pushPublisher.messageStorage(welcomeMessage, welcomeMessage.getMessageType());

        // 推送好友申请通过通知
        LinkApproveFriend notifyUser = new LinkApproveFriend(dto.getUserId(), dto.getFriendId());
        LinkApproveFriend notifyFriend = new LinkApproveFriend(dto.getFriendId(), dto.getUserId());

        this.pushPublisher.push(EventType.APPROVE_FRIEND, Arrays.asList(dto.getUserId()), notifyUser);
        this.pushPublisher.push(EventType.APPROVE_FRIEND, Arrays.asList(dto.getFriendId()), notifyFriend);

        // 推送欢迎消息
        this.pushPublisher.push(EventType.DEFAULT_MESSAGE,
                Arrays.asList(dto.getUserId(), dto.getFriendId()), welcomeMessage);
    }

    public ApiResult friendRequest(LinkFriendRequestDTO dto) {
        if (!stringValidator(dto.getUserId()) || dto.getPage().getLimit() <= 0)
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        int limit = dto.getPage().getLimit() == null
                ? PageRequest.FLOW_LIMIT_DEFAULT
                : Math.min(Math.max(dto.getPage().getLimit(), 1), PageRequest.FLOW_LIMIT_MAX);

        Criteria criteria = where(col(FriendRequest::getFriendId)).is(new ObjectId(dto.getUserId()));
        if (stringValidator(dto.getPage().getCursor())){
            criteria.and(col(FriendRequest::getId)).lt(new ObjectId(dto.getPage().getCursor()));
        }

        Query eq = eq(criteria);
        eq.with(Sort.by(Sort.Direction.DESC,col(FriendRequest::getId)));
        eq.limit(limit + 1);

        List<FriendRequest> friendRequests = this.getMongoTemplate().find(eq, FriendRequest.class);

        boolean hasMore = friendRequests.size() > limit;
        if (hasMore)
            friendRequests = friendRequests.subList(0,limit);

        String nextCursor = hasMore ? friendRequests.get(friendRequests.size() - 1).getId().toHexString() : null;

        List<ObjectId> userId = friendRequests.stream().map(item -> { return item.getUserId();
        }).collect(Collectors.toList());
        Query query = eq(where(col(UserInfo::getId)).in(userId));
        query.fields().include(col(UserInfo::getNickname),col(UserInfo::getAvatar),col(UserInfo::getUserNo));

        Map<ObjectId, UserInfo> map = this.getMongoTemplate().find(query, UserInfo.class).stream().collect(Collectors.toMap(UserInfo::getId, Function.identity()));
        List<LinkFriendRequestVO> request = friendRequests.stream().map(item -> { return LinkFriendRequestVO.fromVo(item, map.get(item.getUserId()));
        }).collect(Collectors.toList());

        Pager<LinkFriendRequestVO> pager = new Pager<LinkFriendRequestVO>()
                .setHasMore(hasMore)
                .setNextCursor(nextCursor)
                .setList(request);

        return ApiResult.success().setData(pager);
    }

    public ApiResult myFriend(LinkMyFriendDTO dto) {
         if (!stringValidator(dto.getUserId()) || dto.getPage().getLimit() <= 0)
             return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        int limit = dto.getPage().getLimit() == null
                ? PageRequest.FLOW_LIMIT_DEFAULT
                : Math.min(Math.max(dto.getPage().getLimit(), 1), PageRequest.FLOW_LIMIT_MAX);

        Criteria criteria = where(col(FriendInfo::getUserId)).is(new ObjectId(dto.getUserId()));
        if (stringValidator(dto.getPage().getCursor()))
            criteria.and(col(FriendInfo::getId)).lt(dto.getPage().getCursor());

        Query eq = eq(criteria);
        eq.with(Sort.by(Sort.Direction.DESC,col(FriendRequest::getId)));
        eq.limit(limit+1);

        List<FriendInfo> friendInfos = this.find(eq);

        boolean hasMore = friendInfos.size() > limit;
        if (hasMore)
            friendInfos = friendInfos.subList(0,limit);
        String nextCursor = hasMore ? friendInfos.get(friendInfos.size() - 1).getId().toHexString() : null;

        List<ObjectId> friendId = friendInfos.stream().map(itme -> { return itme.getFriendId();
        }).collect(Collectors.toList());
        Map<ObjectId, UserInfo> map = this.getMongoTemplate().find(eq(where(col(UserInfo::getId)).in(friendId)), UserInfo.class)
                .stream().collect(Collectors.toMap(UserInfo::getId, Function.identity()));
        List<LinkFriendInfoVO> list = friendInfos.stream().map(v -> { return LinkFriendInfoVO.fromVo(v, map.get(v.getFriendId()));
        }).collect(Collectors.toList());


        Pager<LinkFriendInfoVO> pager = new Pager<LinkFriendInfoVO>()
                .setList(list)
                .setHasMore(hasMore)
                .setNextCursor(nextCursor);

        return ApiResult.success().setData(pager);
    }
}
