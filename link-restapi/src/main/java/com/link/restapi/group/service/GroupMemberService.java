package com.link.restapi.group.service;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.link.common.core.event.EventType;
import com.link.common.core.model.group.LinkRemoveGroup;
import com.link.common.redis.RedisConstant;
import com.link.common.util.id.ChatIdGenerator;
import com.link.im.constants.group.GroupRoleConstant;
import com.link.im.entity.chat.ChatSessionMember;
import com.link.im.entity.group.GroupMember;
import com.link.im.entity.user.UserInfo;
import com.link.im.enums.gloabl.GlobalCode;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.util.ApiResult;
import com.link.restapi.group.model.dto.LinkGetGroupMemberDto;
import com.link.restapi.group.model.dto.LinkCreateGroupDto;
import com.link.restapi.group.model.dto.LinkRemoveGroupMemberDto;
import com.link.restapi.group.model.vo.LinkGroupMemberVo;
import com.link.restapi.push.RemotePushPublisher;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
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

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RemotePushPublisher pushPublisher;

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

    public ApiResult removeMember(LinkRemoveGroupMemberDto dto) {

        // 参数校验
        if (!stringValidator(dto.getGroupId(), dto.getOperationUserId())
                || CollectionUtils.isEmpty(dto.getRemovedUserId())) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        // 校验ObjectId是否合法
        if (!ObjectId.isValid(dto.getGroupId())) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        List<ObjectId> removedUserIds = dto.getRemovedUserId().stream()
                .filter(StringUtils::hasText)
                .distinct()
                .map(item -> {return new ObjectId(item);})
                .toList();

        if (removedUserIds.isEmpty()) {
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        }

        // 查询操作人
        GroupMember operationMember = this.findOne(
                eq(where(col(GroupMember::getGroupId)).is(new ObjectId(dto.getGroupId()))
                        .and(col(GroupMember::getUserId)).is(new ObjectId(dto.getOperationUserId())))
        );

        if (operationMember == null) {
            throw new RuntimeException("操作失败，操作人不在群聊中");
        }

        // 权限判断
        if (operationMember.getRole() != GroupRoleConstant.CREATOR
                && operationMember.getRole() != GroupRoleConstant.ADMINISTRATOR) {
            return ApiResult.error(GlobalCode.NO_PERMISSION);
        }

        // 查询所有待删除成员
        Query query = eq(
                where(col(GroupMember::getGroupId)).is(new ObjectId(dto.getGroupId()))
                        .and(col(GroupMember::getUserId)).in(removedUserIds)
        );

        List<GroupMember> removeMembers = this.find(query);

        if (removeMembers.isEmpty()) {
            return ApiResult.success();
        }

        // 权限校验
        for (GroupMember removeMember : removeMembers) {

            // 不允许删除自己
            if (removeMember.getUserId().toHexString().equals(dto.getOperationUserId())) {
                return ApiResult.error("不能移除自己");
            }

            // 不允许删除群主
            if (removeMember.getRole() == GroupRoleConstant.CREATOR) {
                return ApiResult.error("不能移除群主");
            }

            // 管理员不能删除管理员
            if (operationMember.getRole() == GroupRoleConstant.ADMINISTRATOR
                    && removeMember.getRole() == GroupRoleConstant.ADMINISTRATOR) {
                return ApiResult.error("管理员不能移除其他管理员");
            }
        }

        Query removeQuery = eq(
                where(col(GroupMember::getGroupId)).is(new ObjectId(dto.getGroupId()))
                        .and(col(GroupMember::getUserId)).in(removedUserIds)
        );

        Query updateQuery = eq(
                where(col(ChatSessionMember::getTargetId)).is(new ObjectId(dto.getGroupId()))
                        .and(col(ChatSessionMember::getOwnerId)).in(removedUserIds)
        );
        Update update = update().set(col(ChatSessionMember::isActive), false);

        this.getMongoTemplate().updateMulti(updateQuery,update,ChatSessionMember.class);
        this.remove(removeQuery);
        redisTemplate.delete(RedisConstant.GROUP_MEMBER + ChatIdGenerator.nextId(dto.getGroupId()));

        this.pushPublisher.push(dto.getRemovedUserId(), EventType.REMOVE_GROUP_MEMBER,new LinkRemoveGroup(ChatIdGenerator.nextId(dto.getGroupId())));
        return ApiResult.success();
    }
}
