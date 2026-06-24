package com.link.im.processor.message;

import com.link.im.entity.message.AbstractMessage;
import com.link.im.processor.LinkMessageProcessor;
import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Component
public class LinkGroupMessageProcessor implements LinkMessageProcessor {
    @Override
    public void processor(AbstractMessage message, Channel channel) {

    }
}
