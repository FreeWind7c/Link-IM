package com.link.restapi.module.friend.service;

import com.link.common.core.event.EventType;

import com.link.common.core.model.friend.LinkApproveFriend;
import com.link.common.core.model.friend.LinkFriend;
import com.link.im.enums.friend.FriendApiCode;
import com.link.im.constants.friend.LinkFriendStatus;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.entity.friend.FriendInfo;
import com.link.im.entity.friend.FriendRequest;
import com.link.im.entity.user.UserInfo;
import com.link.restapi.module.friend.model.dto.LinkAddFriendDTO;
import com.link.restapi.module.friend.model.vo.LinkFriendInfoVO;
import com.link.restapi.push.RemotePushPublisher;
import com.link.restapi.module.user.model.dto.LinkApproveFriendDTO;

import com.link.im.util.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

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

    public ApiResult addFriend(LinkAddFriendDTO dto) {
        boolean isUser = this.getMongoTemplate().exists(
                new Query(Criteria.where(col(UserInfo::getId)).is(new ObjectId(dto.getFriendId()))),
                UserInfo.class);
        if (!isUser)
            return ApiResult.error(FriendApiCode.FRIEND_DOES_NOT_EXIST);
        boolean exists = this.exists(new Query(Criteria
                .where(col(FriendInfo::getUserId)).is(new ObjectId(dto.getUserId()))
                .and(col(FriendInfo::getFriendId)).is(new ObjectId(dto.getFriendId())))
        );
        if (exists)
            return ApiResult.error(FriendApiCode.FRIEND_EXIST);
        UserInfo user = this.getMongoTemplate().findById(new ObjectId(dto.getUserId()),UserInfo.class);
        LinkFriend linkFriend = new LinkFriend()
                .setUserId(user.getId().toHexString())
                .setNickname(user.getNickname())
                .setAvatar(user.getAvatar());
        FriendRequest request = new FriendRequest().setUserId(new ObjectId(dto.getUserId())).setFriendId(new ObjectId(dto.getFriendId()))
                .setSource(dto.getSource()).setStatus(0);
        request.setCreatedTime(now());
        request.setUpdatedTime(now());
        this.getMongoTemplate().insert(request);
        this.pushPublisher.push( EventType.ADD_FRIEND,Arrays.asList(dto.getFriendId()), linkFriend);
        return ApiResult.success(FriendApiCode.NOTIFY_USER);
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
            return LinkFriendInfoVO.from(friendInfo, item);
        }).collect(Collectors.toList());


        return ApiResult.success().setData(friends);
    }

    public ApiResult approvePetition(LinkApproveFriendDTO dto) {
        if (dto.getUserId().equals(dto.getFriendId()))
            return ApiResult.error(FriendApiCode.DONT_ADD_SELF);

        ObjectId uid = new ObjectId(dto.getUserId());
        ObjectId fid = new ObjectId(dto.getFriendId());

        FriendRequest request = this.getMongoTemplate().findOne(eq(
                where(col(FriendRequest::getUserId)).is(new ObjectId(dto.getFriendId()))
                        .and(col(FriendRequest::getFriendId)).is(new ObjectId(dto.getUserId()))
        ), FriendRequest.class);
        FriendInfo existed = this.findOne(eq(
                where(col(FriendInfo::getUserId)).is(uid)
                        .and(col(FriendInfo::getFriendId)).is(fid)));

        if (existed != null && existed.getStatus() == LinkFriendStatus.F)
            return ApiResult.error(FriendApiCode.FRIEND_EXIST);

        long ts = now();
        if (existed != null) {
            // 之前被删除/拉黑过，对称恢复两条记录
            Query both = eq(where(col(FriendInfo::getUserId)).in(uid, fid)
                    .and(col(FriendInfo::getFriendId)).in(uid, fid));
            this.updateMulti(both, new Update()
                    .set(col(FriendInfo::getStatus), LinkFriendStatus.F)
                    .set(col(FriendInfo::getUpdatedTime), ts));
        } else {
            FriendInfo user = new FriendInfo().create(dto.getUserId(), dto.getFriendId(), request.getSource());
            user.setCreatedTime(ts);
            user.setUpdatedTime(ts);

            FriendInfo friend = new FriendInfo().create(dto.getFriendId(), dto.getUserId(), request.getSource());
            friend.setCreatedTime(ts);
            friend.setUpdatedTime(ts);

            this.getMongoTemplate().insert(List.of(user, friend), FriendInfo.class);
        }

        // 无论新增还是恢复，双方都需要同步好友关系
        LinkApproveFriend notifySelf = new LinkApproveFriend(dto.getUserId(), dto.getFriendId());
        this.pushPublisher.push(EventType.APPROVE_FRIEND,Arrays.asList(dto.getUserId()),  notifySelf);
        LinkApproveFriend notifyFriend = new LinkApproveFriend(dto.getFriendId(), dto.getUserId());
        this.pushPublisher.push(EventType.APPROVE_FRIEND,Arrays.asList(dto.getFriendId()),  notifyFriend);
        return ApiResult.success();
    }
}
