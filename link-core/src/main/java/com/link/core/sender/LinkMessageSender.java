package com.link.core.sender;

import com.link.common.core.event.EventType;
import io.netty.channel.Channel;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public interface LinkMessageSender {


    public void send(EventType event, Channel channel, Object obj);


    void send(EventType event, List<Channel> channels, Object obj);
}

