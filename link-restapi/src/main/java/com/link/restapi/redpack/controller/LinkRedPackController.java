package com.link.restapi.redpack.controller;

import com.link.im.entity.redpack.RedPacket;
import com.link.im.util.ApiResult;
import com.link.restapi.redpack.model.dto.LinkGrabPacketDto;
import com.link.restapi.redpack.model.dto.LinkSendPacketDto;
import com.link.restapi.redpack.service.RedPackService;
import io.lettuce.core.XReadArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@RestController
@RequestMapping("/red-pack")
public class LinkRedPackController {


    @Autowired
    private RedPackService redPackService;


    @PostMapping("/send-packet")
    public ApiResult sendRedPacket(@RequestBody LinkSendPacketDto dto){
        return redPackService.sendRedPacket(dto);
    }

    @PostMapping("/grab-packet")
    public ApiResult grabRedPacket(@RequestBody LinkGrabPacketDto dto){
        return redPackService.grabRedPacket(dto);
    }


}
