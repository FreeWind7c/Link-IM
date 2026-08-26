package com.link.restapi.module.message.controller;

import com.link.im.dto.message.DefaultMessageDTO;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.handler.LinkDefaultMessageEventHandler;
import com.link.restapi.utils.ApiResult;
import com.link.restapi.module.message.model.dto.LinkAroundMessageDto;
import com.link.restapi.module.message.model.dto.LinkCompleteMessageDto;
import com.link.restapi.module.message.model.dto.LinkPullMessageDto;
import com.link.restapi.module.message.service.LinkMessageInfoService;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
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


    @Autowired
    private LinkDefaultMessageEventHandler handler;






    @PostMapping("/pull-message")
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
