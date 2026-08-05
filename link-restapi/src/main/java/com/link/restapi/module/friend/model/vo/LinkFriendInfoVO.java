package com.link.restapi.module.friend.model.vo;

import com.link.im.entity.friend.FriendInfo;
import com.link.im.entity.user.UserInfo;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月22日
 */
@Data
@Accessors(chain = true)
public class LinkFriendInfoVO {


    private String userId;

    private String nickname;

    private String avatar;

    private String remark;
    /**
     * 状态 1=正常，2=删除，3=黑名单
     */
    private int status;
    /**
     * 来源	1.id添加	2.账号名添加	3.二维码添加	4.二维码添加	5.群聊添加
     */
    private int source;


    private boolean showTop;

    // 免打扰
    private boolean silence;

    /** 由好友关系实体 + 对端用户信息组装展示 VO。 */
    public static LinkFriendInfoVO from(FriendInfo friendInfo, UserInfo user) {
        return new LinkFriendInfoVO().setUserId(friendInfo.getFriendId().toHexString())
                .setNickname(user.getNickname()).setAvatar(user.getAvatar())
                .setRemark(friendInfo.getRemark()).setStatus(friendInfo.getStatus())
                .setSource(friendInfo.getSource()).setShowTop(friendInfo.isShowTop())
                .setSilence(friendInfo.isSilence());
    }
}
