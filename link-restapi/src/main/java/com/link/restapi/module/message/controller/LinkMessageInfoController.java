package com.link.restapi.module.message.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.template.SentinelMethodTemplate;
import com.link.restapi.config.sentinel.SentinelBlockHandler;
import com.link.restapi.utils.ApiResult;
import com.link.restapi.module.message.model.dto.LinkAroundMessageDto;
import com.link.restapi.module.message.model.dto.LinkCompleteMessageDto;
import com.link.restapi.module.message.model.dto.LinkPullMessageDto;
import com.link.restapi.module.message.service.LinkMessageInfoService;
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
    private LinkMessageInfoService linkMessageInfoService;


    @PostMapping("/pull-message")
    @SentinelResource(
            value = SentinelMethodTemplate.PULL_MESSAGE,
            blockHandlerClass = SentinelBlockHandler.class,
            blockHandler = SentinelMethodTemplate.HANDLE
    )
    public ApiResult pullMessage(@RequestBody LinkPullMessageDto dto)
    {
        return linkMessageInfoService.pullMessage(dto);
    }

    @PostMapping("/complete-message")
    public ApiResult completeMessage(@RequestBody LinkCompleteMessageDto dto){
        return linkMessageInfoService.completeMessage(dto);
    }

    @PostMapping("/around-message")
    public ApiResult aroundMessage(@RequestBody LinkAroundMessageDto dto){
        return linkMessageInfoService.aroundMessage(dto);
    }
}
