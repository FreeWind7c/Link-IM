package com.link.server.controller;

import com.google.gson.Gson;
import com.link.im.dto.message.DefaultMessageDTO;
import com.link.im.entity.data.TextData;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.handler.LinkDefaultMessageEventHandler;
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
 * @CreateTime: 2026年08月23日
 */
@RestController
@RequestMapping("/message")
public class MessageController {

    @Autowired
    private LinkDefaultMessageEventHandler handler;

    @PostMapping("/send")
    public void send(@RequestBody DefaultMessageDTO dto){
        DefaultMessageInfo info = new DefaultMessageInfo();
        BeanUtils.copyProperties(dto,info);
        TextData textData = new TextData();
        textData.setContent("测试消息"+System.currentTimeMillis());
        info.setId(new ObjectId(dto.getId()));
        info.setSndId(new ObjectId(dto.getSndId()));
        info.setRcvId(new ObjectId(dto.getRcvId()));
        info.setId(new ObjectId());
        info.setData(textData.toJson());
        info.setBaseData(textData);
        info.setTimestamp(System.currentTimeMillis());
        System.out.println("dto: " + new Gson().toJson(dto));
        handler.handler(info,new NioSocketChannel());

    }

}
