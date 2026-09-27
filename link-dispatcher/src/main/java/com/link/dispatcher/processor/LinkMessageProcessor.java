package com.link.dispatcher.processor;

import com.link.base.entity.base.BaseMessage;
import io.netty.channel.Channel;
import org.springframework.stereotype.Service;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Service
public interface LinkMessageProcessor {

    public void processor(BaseMessage message, Channel channel);

}
