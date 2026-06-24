package com.link.restapi.chat.controller;

import com.link.im.util.R;
import com.link.restapi.chat.model.dto.LinkReportSessionDto;
import com.link.restapi.chat.service.ChatMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月24日
 */
@RequestMapping("/api/chat")
@RestController
public class LinkChatMemberController {


    @Autowired
    private ChatMemberService chatMemberService;

    @PostMapping("/report-session")
    public R reportSession(@RequestBody LinkReportSessionDto dto){
        return chatMemberService.reportSession(dto);
    }

}
