package com.link.restapi.module.redpack.controller;

import com.link.restapi.utils.ApiResult;
import com.link.restapi.module.redpack.model.dto.LinkGetRedPacketDTO;
import com.link.restapi.module.redpack.service.RedPackService;
import com.link.restapi.module.redpack.model.dto.LinkGrabPacketDto;
import com.link.restapi.module.redpack.model.dto.LinkSendPacketDto;
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

    @PostMapping("/get-red-packet")
    public ApiResult getRedPacket(@RequestBody LinkGetRedPacketDTO dto){
        return redPackService.getRedPacket(dto);
    }

    @PostMapping("/send-packet")
    public ApiResult sendRedPacket(@RequestBody LinkSendPacketDto dto){
        return redPackService.sendRedPacket(dto);
    }

    @PostMapping("/grab-packet")
    public ApiResult grabRedPacket(@RequestBody LinkGrabPacketDto dto){
        return redPackService.grabRedPacket(dto);
    }


}
