package com.link.restapi.group.model.vo;

import com.link.im.entity.group.GroupMember;
import com.link.im.entity.user.UserInfo;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月27日
 */
@Data
@Accessors(chain = true)
public class LinkGroupMemberVo {

    private String userId;

    private String groupId;

    private int role;

    private String nickname;

    private String avatar;

    private int source;

    private String inviterUserId;

    private boolean foreverSilence;

    private long noSpeakingUntil;

    public List<LinkGroupMemberVo> createVos(Map<ObjectId, UserInfo> userMap, List<GroupMember> groupMembers) {
        List<LinkGroupMemberVo> vos = groupMembers.stream().map(item -> {
            LinkGroupMemberVo vo = new LinkGroupMemberVo();
            BeanUtils.copyProperties(item, vo);
            vo.setUserId(item.getUserId().toHexString())
              .setInviterUserId(item.getInviterUserId().toHexString())
              .setAvatar(userMap.get(item.getUserId()).getAvatar())
              .setNickname(StringUtils.isEmpty(item.getNickname())
                    ? userMap.get(item.getUserId()).getNickname()
                    : item.getNickname());
            return vo;
        }).collect(Collectors.toList());
        return vos;
    }
}
