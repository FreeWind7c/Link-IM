package com.link.restapi.group.controller;

import com.link.im.util.ApiResult;
import com.link.restapi.group.model.dto.LinkCreateGroupDto;
import com.link.restapi.group.service.GroupInfoService;
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

    @PostMapping("/create-group")
    public ApiResult createGroup(@RequestBody LinkCreateGroupDto dto){
        return groupInfoService.createGroup(dto);
    }
}
