package com.link.restapi.module.friend.model.vo;

import com.link.im.entity.friend.FriendRequest;
import com.link.im.entity.user.UserInfo;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月25日
 */
@Data
@Accessors(chain = true )
public class LinkFriendRequestVO {


    private String id;

    private String friendNo;

    private String friendId;

    private String friendName;

    private String friendAvatar;

    private int source;

    // 0未处理 1已同意 2已拒绝
    private int status;


    public static LinkFriendRequestVO fromVo(FriendRequest item, UserInfo userInfo) {
        LinkFriendRequestVO vo = new LinkFriendRequestVO();
        vo.setId(item.getId().toHexString());
        vo.setFriendNo(userInfo.getUserNo());
        vo.setFriendId(item.getUserId().toHexString());
        vo.setFriendName(userInfo.getNickname());
        vo.setFriendAvatar(userInfo.getAvatar());
        vo.setSource(item.getSource());
        vo.setStatus(item.getStatus());
        return vo;
    }
}
