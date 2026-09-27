package com.link.restapi.module.group.service;


import com.link.common.core.event.EventType;
import com.link.common.core.model.group.LinkRemoveGroup;
import com.link.common.redis.RedisKeys;
import com.link.common.util.id.ChatIdGenerator;
import com.link.common.constants.group.GroupRoleConstant;
import com.link.base.entity.chat.ChatSessionMember;
import com.link.base.entity.group.GroupInfo;
import com.link.base.entity.group.GroupMember;
import com.link.base.entity.user.UserInfo;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.restapi.utils.ApiResult;
import com.link.restapi.module.group.model.dto.LinkGetGroupMemberDto;
import com.link.restapi.module.group.model.dto.LinkCreateGroupDto;
import com.link.restapi.module.group.model.dto.LinkRemoveGroupMemberDto;
import com.link.restapi.module.group.model.vo.LinkGroupMemberVo;
import com.link.restapi.push.RemotePushPublisher;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
            GroupMember groupMember = new GroupMember().create(item.getUserId(), dto.getOwnerId(), GroupRoleConstant.REGULAR_MEMBER);
            groupMember.setCreatedTime(now);
            groupMember.setCreatedTime(now);
            return groupMember;
        }).collect(Collectors.toList());
        members.add(new GroupMember().create(dto.getOwnerId(),dto.getOwnerId(), GroupRoleConstant.CREATOR));
        return members;
    }

    public ApiResult getMember(LinkGetGroupMemberDto dto) {
        if (!stringValidator(dto.getGroupId()) || !pageValidator(dto.getOffset(),dto.getLimit()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        Query eq = eq(
                where(col(GroupMember::getGroupId)).is(new ObjectId(dto.getGroupId()))
        );
        eq.with(Sort.by(
                        Sort.Order.asc(col(GroupMember::getRole)),
                        Sort.Order.asc(col(GroupMember::getCreatedTime)))
        );
        List<GroupMember> groupMembers = this.find(eq);
        if (groupMembers == null || groupMembers.isEmpty())
            return ApiResult.success();

        Query query = eq(where(col(UserInfo::getId)).in(groupMembers.stream().map(item -> { return item.getUserId();
        }).toList()));
        query.fields().include(col(UserInfo::getNickname),col(UserInfo::getAvatar));
        Map<ObjectId, UserInfo> userMap = this.getMongoTemplate().find(query, UserInfo.class)
                .stream().collect(Collectors.toMap(UserInfo::getId, Function.identity()));

        return ApiResult.success().setData( new LinkGroupMemberVo().createVos(userMap,groupMembers));
    }
}
