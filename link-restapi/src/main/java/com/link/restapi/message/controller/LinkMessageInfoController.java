package com.link.restapi.message.controller;

import com.link.im.util.R;
import com.link.restapi.message.model.dto.LinkPullMessageDto;
import com.link.restapi.message.service.MessageInfoService;
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
    public R pullMessage(@RequestBody LinkPullMessageDto dto)
    {
        return messageInfoService.pullMessage(dto);
    }
}
