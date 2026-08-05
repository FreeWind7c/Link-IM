package com.link.restapi.module.message.controller;

import com.link.im.util.ApiResult;
import com.link.restapi.module.message.model.dto.LinkAroundMessageDto;
import com.link.restapi.module.message.model.dto.LinkCompleteMessageDto;
import com.link.restapi.module.message.model.dto.LinkPullMessageDto;
import com.link.restapi.module.message.service.MessageInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月23日
 */
@RequestMapping("/message")
@RestController
public class LinkMessageInfoController {


    @Autowired
    private MessageInfoService messageInfoService;




    @PostMapping("/pull-message")
    public ApiResult pullMessage(@RequestBody LinkPullMessageDto dto)
    {
        return messageInfoService.pullMessage(dto);
    }

    @PostMapping("/complete-message")
    public ApiResult completeMessage(@RequestBody LinkCompleteMessageDto dto){
        return messageInfoService.completeMessage(dto);
    }

    @PostMapping("/around-message")
    public ApiResult aroundMessage(@RequestBody LinkAroundMessageDto dto){
        return messageInfoService.aroundMessage(dto);
    }
}
