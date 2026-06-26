package com.link.restapi.user.controller;

import com.link.im.util.ApiResult;

import com.link.restapi.user.model.dto.LinkUserAuthDTO;

import com.link.restapi.user.model.dto.LinkUserRegisterDTO;
import com.link.restapi.user.service.UserInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月20日
 */
@RestController
@RequestMapping("/user")
public class LinkUserInfoCtrl {



    @Autowired
    private UserInfoService userInfoService;


    @PostMapping("/search-user")
    public ApiResult searchFriend(@RequestParam String userNo){
        return userInfoService.searchUser(userNo);
    }

    @PostMapping("/auth")
    public ApiResult userAuth(@RequestBody LinkUserAuthDTO dto){
        return userInfoService.userAuth(dto);
    }

    @PostMapping("/register")
    public ApiResult userRegister(@RequestBody LinkUserRegisterDTO dto){
        return userInfoService.userRegister(dto);
    }

}
