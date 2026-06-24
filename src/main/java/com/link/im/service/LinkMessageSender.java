package com.link.im.service;

import com.link.core.config.LinkCoreConfig;
import com.link.core.event.EventType;
import io.netty.channel.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 */
@Component
public class LinkMessageSender {

    @Autowired
    private LinkCoreConfig config;


    public void send(String userId,EventType eventType,Object obj){

        List<Channel> channels = this.config.getSessionManager().getSession(userId).stream().map(item -> {
            return item.getChannel();
        }).toList();
        this.config.getLinkSender().send(eventType,channels,obj);
    }


}
