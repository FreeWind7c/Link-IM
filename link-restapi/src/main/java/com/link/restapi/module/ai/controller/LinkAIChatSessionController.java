package com.link.restapi.module.ai.controller;

import com.link.restapi.module.ai.model.dto.LinkQueryChatSessionDTO;
import com.link.restapi.module.ai.service.LinkAIChatSessionService;
import com.link.restapi.utils.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */
@RestController
@RequestMapping("/ai/chat")
public class LinkAIChatSessionController {


    @Autowired
    private LinkAIChatSessionService service;

    @PostMapping("/query-chat")
    public ApiResult queryChat(@RequestBody LinkQueryChatSessionDTO dto){
        return service.queryChat(dto);
    }


}
