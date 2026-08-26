package com.link.restapi.module.friend.controller;

import com.google.gson.Gson;
import com.link.restapi.module.friend.model.dto.LinkAddFriendDTO;
import com.link.restapi.module.friend.model.dto.LinkFriendRequestDTO;
import com.link.restapi.module.friend.model.dto.LinkMyFriendDTO;
import com.link.restapi.module.friend.service.FriendInfoService;
import com.link.restapi.utils.ApiResult;
import com.link.restapi.module.user.model.dto.LinkApproveFriendDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 */
@RequestMapping("/friend")
@RestController
public class LinkFriendInfoController {

    @Autowired
    private FriendInfoService friendInfoService;




    @PostMapping("my-friend")
    public ApiResult myFriend(@RequestBody LinkMyFriendDTO dto){
        return friendInfoService.myFriend(dto);
    }

    @PostMapping("/friend-request")
    public ApiResult friendRequest(@RequestBody LinkFriendRequestDTO dto)
    {
        return friendInfoService.friendRequest(dto);
    }


    @PostMapping("/approve-petition")
    public ApiResult approvePetition(@RequestBody LinkApproveFriendDTO dto){
        return friendInfoService.approvePetition(dto);
    }

    @PostMapping("/query-friend")
    public ApiResult queryFriend(@RequestParam String userId){
        return friendInfoService.queryFriend(userId);
    }

    @PostMapping("/add-friend")
    public ApiResult addFriend(@RequestBody LinkAddFriendDTO dto){
        return friendInfoService.addFriend(dto);
    }


}
