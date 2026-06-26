package com.link.restapi.group.service;

import com.link.im.constants.group.GroupRoleConstant;
import com.link.im.entity.group.GroupMember;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.restapi.group.model.dto.LinkCreateGroupDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月26日
 */
@Slf4j
@Component
public class GroupMemberService extends BasePlatFormMongoService<GroupMember> {

    public List<GroupMember> createMembers(LinkCreateGroupDto dto) {

        long now = now();
        
        List<GroupMember> members = dto.getMemebrs().stream().map(item -> {
            GroupMember groupMember = new GroupMember().create(item.getUserId(),item.getNickname(), dto.getOwnerId(), GroupRoleConstant.REGULAR_MEMBER);
            groupMember.setCreatedTime(now);
            groupMember.setCreatedTime(now);
            return groupMember;
        }).collect(Collectors.toList());
        members.add(new GroupMember().create(dto.getOwnerId(),dto.getOwnerName(),dto.getOwnerId(), GroupRoleConstant.CREATOR));
        return members;
    }
}
