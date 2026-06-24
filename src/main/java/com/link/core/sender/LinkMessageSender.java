package com.link.core.sender;

import com.link.core.event.EventType;
import io.netty.channel.Channel;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public interface LinkMessageSender {


    public void send(EventType event, Channel channel, Object obj);

    /**
     * 把同一条消息群发给多个 channel。
     *
     * <p>消息只序列化一次，复用同一个 PackData 对象广播给所有 channel；底层借助 Netty 的
     * ChannelGroup 完成，已关闭的 channel 会被自动忽略。
     *
     * @param event    事件类型，决定协议 action
     * @param channels 目标 channel 集合，为空则直接返回
     * @param obj      待发送的消息体
     */
    void send(EventType event, List<Channel> channels, Object obj);
}

