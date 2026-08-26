package com.link.restapi.module.ai.controller;

import com.link.restapi.module.ai.service.LinkAIUserInfoService;
import com.link.restapi.module.ai.model.dto.LinkAIQueryUserDTO;
import com.link.restapi.utils.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月26日
 */
@RestController
@RequestMapping("/ai/user")
public class LinkAIUserInfoController {

    @Autowired
    private LinkAIUserInfoService service;




    @PostMapping("/query-user-info")
    public ApiResult queryUserInfo(@RequestBody LinkAIQueryUserDTO dto){
        return service.queryUserInfo(dto);
    }

}
