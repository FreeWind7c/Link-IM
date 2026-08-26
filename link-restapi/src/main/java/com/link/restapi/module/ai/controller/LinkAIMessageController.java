package com.link.restapi.module.ai.controller;

import com.google.gson.Gson;
import com.link.restapi.module.ai.model.dto.LinkSearchMessageDTO;
import com.link.restapi.module.message.model.dto.LinkAIPullMessageDTO;
import com.link.restapi.module.ai.service.LinkAIMessageInfoService;
import com.link.restapi.utils.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月26日
 */

@RestController
@RequestMapping("/ai/message")
public class LinkAIMessageController {

    @Autowired
    private LinkAIMessageInfoService messageInfoService;


    @PostMapping("/search-message")
    public ApiResult searchMessage(@RequestBody LinkSearchMessageDTO dto){
        return messageInfoService.searchMessage(dto);
    }

    @PostMapping("/pull-message")
    public ApiResult pullMessage(@RequestBody LinkAIPullMessageDTO dto){
        return messageInfoService.pullMessage(dto);
    }



}
