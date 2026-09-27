package com.link.restapi.module.user.controller;

import com.link.base.redis.BasePlatFormRedisService;
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
    private BasePlatFormRedisService redisService;





    @PostMapping("/create-bot")
    public ApiResult createBot(){
        return userInfoService.createBot();
    }

    /**
     * 初始化基础数据：先建机器人，再建 admin/test/user/... 等初始化用户
     * （每人绑定机器人好友并推送欢迎消息），最后让其余用户与 admin 建立好友关系。
     * 幂等，可重复调用。
     */
    @PostMapping("/init")
    public ApiResult initData(){
        return userInfoService.initData();
    }

    @PostMapping("/verify-survival-status")
    public ApiResult verifySurvivalStatus(@RequestBody LinkUserReportTokenDto dto){
        return userInfoService.verifySurvivalStatus(dto);
    }


    @PostMapping("/search-user")
    public ApiResult searchFriend(@RequestParam String userNo){
        return userInfoService.searchUser(userNo);
    }

    @PostMapping("/login")
    public ApiResult userLogin(@RequestBody LinkUserAuthDTO dto){
        return userInfoService.userLogin(dto);
    }

    @PostMapping("/register")
    public ApiResult userRegister(@RequestBody LinkUserRegisterDTO dto){
        return userInfoService.userRegister(dto);
    }

}
