package com.link.restapi.group.service;

import com.link.common.util.id.ChatIdGenerator;
import com.link.im.entity.chat.ChatMember;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.group.GroupInfo;
import com.link.im.entity.group.GroupMember;
import com.link.im.enums.gloabl.GlobalCode;
import com.link.im.enums.group.GroupInfoCode;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.util.ApiResult;
import com.link.restapi.group.model.dto.LinkCreateGroupDto;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月26日
 */
@Slf4j
@Component
public class GroupInfoService extends BasePlatFormMongoService<GroupInfo> {


    @Autowired
    private GroupMemberService groupMemberService;

    @Transactional
    public ApiResult createGroup(LinkCreateGroupDto dto) {
        if (!stringValidator(dto.getOwnerId()) || dto.getMemebrs().size() < 1)
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        List<GroupMember> members = this.groupMemberService.createMembers(dto);
        List<ObjectId> memberIds = members.stream().map(item -> { return item.getUserId();
        }).collect(Collectors.toList());

        GroupInfo groupInfo = new GroupInfo().create(dto.getOwnerId(), dto.getOwnerName(), memberIds);
        GroupInfo group = this.insert(groupInfo);
        members.forEach(item -> {item.setGroupId(group.getId());});
        this.getMongoTemplate().insert(members,GroupMember.class);

        String chatId = ChatIdGenerator.nextId(group.getId().toHexString());
        ChatSession session = new ChatSession().createGroup(chatId);
        List<ChatMember> chatMembers = members.stream().map(item -> {
            return new ChatMember().createGroup(item.getUserId().toHexString(), group.getId().toHexString(), chatId);
        }).collect(Collectors.toList());

        this.getMongoTemplate().insert(session);
        this.getMongoTemplate().insert(chatMembers, ChatMember.class);
        return ApiResult.success(GroupInfoCode.GROUP_CREATE_SUCCESS);
    }
}
