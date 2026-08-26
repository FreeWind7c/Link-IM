package com.link.restapi.module.user.controller;

import com.link.restapi.utils.ApiResult;

import com.link.restapi.module.user.model.dto.LinkUserAuthDTO;

import com.link.restapi.module.user.model.dto.LinkUserRegisterDTO;
import com.link.restapi.module.user.model.dto.LinkUserReportTokenDto;
import com.link.restapi.module.user.service.UserInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月20日
 */
@RestController
@RequestMapping("/user")
public class LinkUserInfoCtrl {



    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private RedisTemplate redisTemplate;






    @PostMapping("/verify-survival-status")
    public ApiResult verifySurvivalStatus(@RequestBody LinkUserReportTokenDto dto){
        return userInfoService.verifySurvivalStatus(dto);
    }


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
