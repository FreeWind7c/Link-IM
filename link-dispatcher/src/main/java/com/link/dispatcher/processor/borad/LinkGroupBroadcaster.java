package com.link.dispatcher.processor.borad;

import com.link.common.core.event.EventType;
import com.link.common.core.model.data.PackData;
import com.link.core.codec.LinkPackDataEncoder;
import com.link.core.config.LinkCoreConfig;
import com.link.base.entity.message.GroupMessageInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月26日
 */
@Slf4j
@Component
public class LinkGroupBroadcaster {

    @Autowired
    private LinkCoreConfig config;

    @Autowired
    private LinkPackDataEncoder encoder;


    public void broadcast(EventType event, List<Channel> channels, Object obj){
        if (channels == null || channels.isEmpty())
            return;

        GroupMessageInfo message = (GroupMessageInfo) obj;
        byte[] body = this.config.getLinkSerializer().serialize(message);
        broadcast(event, channels, body);
    }

    public void broadcast(EventType event, List<Channel> channels, byte[] body) {
        PackData packData = new PackData(event.getAction(), body.length, body);
        ByteBuf frame = PooledByteBufAllocator.DEFAULT.buffer(LinkCoreConfig.PROTO_FRAME_LENGTH + body.length);
        try{
            this.encoder.encode(packData,frame);
            IdentityHashMap<EventLoop, List<Channel>> eventMap = new IdentityHashMap<>();

            for (Channel channel : channels) {
                if (channel == null || !channel.isActive())
                    continue;
                eventMap.computeIfAbsent(channel.eventLoop(),k -> new ArrayList<>()).add(channel);
            }

            for (Map.Entry<EventLoop, List<Channel>> entry : eventMap.entrySet()) {
                List<Channel> group = entry.getValue();
                ByteBuf buf = frame.retainedDuplicate();
                try{
                    entry.getKey().execute(() -> {
                        try{
                            for (Channel channel : group) {
                                if (!channel.isActive()) continue;;
                                if (!channel.isWritable()){
                                    log.debug("channel {} 不可写(慢消费者),跳过本条群消息", channel.id());
                                    continue;
                                }
                                channel.write(buf.retainedDuplicate(),channel.voidPromise());
                            }
                            for (Channel channel : group) {
                                if (!channel.isActive())
                                    continue;
                                channel.flush();
                            }
                        }finally {
                            buf.release();
                        }
                    });
                }catch (Exception e){
                    buf.release();
                }
            }
        }finally{
            frame.release();
        }
    }

}
