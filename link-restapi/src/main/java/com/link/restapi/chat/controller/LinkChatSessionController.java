package com.link.restapi.chat.controller;

import com.link.im.util.ApiResult;
import com.link.restapi.chat.model.dto.LinkCreateChatDto;
import com.link.restapi.chat.model.dto.LinkPullChatDTO;
import com.link.restapi.chat.service.ChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月22日
 */
@RequestMapping("/chat")
@RestController
public class LinkChatSessionController {

    @Autowired
    private ChatSessionService chatSessionService;



    @PostMapping("create-chat")
    public ApiResult createChat(@RequestBody LinkCreateChatDto dto){
        return chatSessionService.createChat(dto);
    }

    @PostMapping("/pull-chat")
    public ApiResult pullChat (@RequestBody LinkPullChatDTO dto){
         return chatSessionService.pullChat(dto);
    }

}
