package com.link.restapi.module.chat.controller;

import com.link.im.entity.message.DefaultMessageInfo;
import com.link.restapi.utils.ApiResult;
import com.link.restapi.module.chat.model.dto.LinkCreateChatDto;
import com.link.restapi.module.chat.model.dto.LinkPullChatDTO;
import com.link.restapi.module.chat.model.dto.LinkQueryChatDTO;
import com.link.restapi.module.chat.service.ChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月22日
 */
@RequestMapping("/chat")
@RestController
public class LinkChatSessionController {


    @Autowired
    private ChatSessionService chatSessionService;



    @PostMapping("/get-chat")
    public ApiResult getChat(@RequestBody LinkQueryChatDTO dto){
        return chatSessionService.getChat(dto);
    }

    @PostMapping("create-chat")
    public ApiResult createChat(@RequestBody LinkCreateChatDto dto){
        return chatSessionService.createChat(dto);
    }

    @PostMapping("/pull-chat")
    public ApiResult pullChat (@RequestBody LinkPullChatDTO dto){
         return chatSessionService.pullChat(dto);
    }

}
