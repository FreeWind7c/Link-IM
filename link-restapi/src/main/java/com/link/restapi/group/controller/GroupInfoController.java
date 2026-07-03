package com.link.restapi.group.controller;

import com.link.im.util.ApiResult;
import com.link.restapi.group.model.dto.LinkGetGroupMemberDto;
import com.link.restapi.group.model.dto.LinkAddGroupAdministratorDto;
import com.link.restapi.group.model.dto.LinkCreateGroupDto;
import com.link.restapi.group.model.dto.LinkJoinGroupDto;
import com.link.restapi.group.model.dto.LinkRemoveGroupMemberDto;
import com.link.restapi.group.service.GroupInfoService;
import com.link.restapi.group.service.GroupMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月26日
 */
@RestController
@RequestMapping("/group")
public class GroupInfoController {

    @Autowired
    private GroupInfoService groupInfoService;

    @Autowired
    private GroupMemberService groupMemberService;



    @PostMapping("/remove-member")
    public ApiResult removeMember(@RequestBody LinkRemoveGroupMemberDto dto){
        return groupInfoService.removeMember(dto);
    }

    @PostMapping("/get-member")
    public ApiResult getMember(@RequestBody LinkGetGroupMemberDto dto) {
        return groupMemberService.getMember(dto);
    }

    @PostMapping("/add-administrator")
    public ApiResult addAdministrator(@RequestBody LinkAddGroupAdministratorDto dto){
        return groupInfoService.addAdministrator(dto);
    }

    @PostMapping("/join-group")
    public ApiResult joinGroup(@RequestBody LinkJoinGroupDto dto){
        return groupInfoService.joinGroup(dto);
    }


    @PostMapping("/create-group")
    public ApiResult createGroup(@RequestBody LinkCreateGroupDto dto){
        return groupInfoService.createGroup(dto);
    }
}
