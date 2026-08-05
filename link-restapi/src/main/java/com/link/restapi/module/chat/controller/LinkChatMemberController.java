package com.link.restapi.module.chat.controller;

import com.link.im.util.ApiResult;
import com.link.restapi.module.chat.model.dto.LinkReportSessionDto;
import com.link.restapi.module.chat.service.ChatMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月24日
 */
@RequestMapping("/chat")
@RestController
public class LinkChatMemberController {


    @Autowired
    private ChatMemberService chatMemberService;

    @PostMapping("/report-session")
    public ApiResult reportSession(@RequestBody LinkReportSessionDto dto){
        return chatMemberService.reportSession(dto);
    }

}
