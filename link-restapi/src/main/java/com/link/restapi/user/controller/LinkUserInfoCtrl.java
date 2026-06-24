package com.link.restapi.user.controller;

import com.link.im.util.R;

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
@RequestMapping("/api/user")
public class LinkUserInfoCtrl {



    @Autowired
    private UserInfoService userInfoService;


    @PostMapping("/search-user")
    public R searchFriend(@RequestParam String userNo){
        return userInfoService.searchUser(userNo);
    }

    @PostMapping("/auth")
    public R userAuth(@RequestBody LinkUserAuthDTO dto){
        return userInfoService.userAuth(dto);
    }

    @PostMapping("/register")
    public R userRegister(@RequestBody LinkUserRegisterDTO dto){
        return userInfoService.userRegister(dto);
    }

}
