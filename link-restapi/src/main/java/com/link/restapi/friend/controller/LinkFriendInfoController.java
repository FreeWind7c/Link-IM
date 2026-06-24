package com.link.restapi.friend.controller;

import com.link.restapi.friend.model.dto.LinkAddFriendDTO;
import com.link.restapi.friend.service.FriendInfoService;
import com.link.im.util.R;
import com.link.restapi.user.model.dto.LinkApproveFriendDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 */
@RequestMapping("/api/friend")
@RestController
public class LinkFriendInfoController {

    @Autowired
    private FriendInfoService friendInfoService;



    @PostMapping("/approve-petition")
    public R approvePetition(@RequestBody LinkApproveFriendDTO dto){
        return friendInfoService.approvePetition(dto);
    }

    @PostMapping("/query-friend")
    public R queryFriend(@RequestParam String userId){
        return friendInfoService.queryFriend(userId);
    }



    @PostMapping("/add-friend")
    public R addFriend(@RequestBody LinkAddFriendDTO dto){
        return friendInfoService.addFriend(dto);
    }


}
