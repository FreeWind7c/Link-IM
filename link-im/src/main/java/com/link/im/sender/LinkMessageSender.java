package com.link.im.sender;

import com.google.gson.Gson;
import com.link.common.core.model.data.PackData;
import com.link.core.codec.LinkPackDataEncoder;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.session.service.LinkSession;
import com.link.im.processor.borad.LinkGroupBroadcaster;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 */
@Slf4j
@Component
public class LinkMessageSender {

    @Autowired
    private LinkCoreConfig config;

    @Autowired
    private LinkGroupBroadcaster broadcaster;

    @Autowired
    private LinkPackDataEncoder encoder;


    public void send(String userId,EventType eventType,Object obj){

        List<LinkSession> sessions = this.config.getSessionManager().getSession(userId);

        if (sessions == null || sessions.isEmpty()) {
            log.debug("用户 {} 在本进程无在线 session，跳过在线推送（待 Redis/MQ 接入）", userId);
            return;
        }
        List<Channel> channels = sessions.stream().map(LinkSession::getChannel).toList();
        this.config.getLinkSender().send(eventType,channels,obj);
    }


    public void send(List<String> userId, EventType event, Object payload) {
        ArrayList<Channel> channels = new ArrayList<>();
        userId.forEach(key -> {
            List<LinkSession> session = this.config.getSessionManager().getSession(key);
            if (session != null && !session.isEmpty()){
                session.forEach(s -> {
                    channels.add(s.getChannel());
                });
            }
        });

        if (channels.size() > this.config.getCorePoolSize())
            this.broadcaster.broadcast(event,channels, this.config.getLinkSerializer().serialize(payload));
        else {
            byte[] body = this.config.getLinkSerializer().serialize(payload);
            PackData packData = new PackData(event.getAction(), body.length, body);

            ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(LinkCoreConfig.PROTO_FRAME_LENGTH+body.length);
            this.encoder.encode(packData,buffer);
            try{

                for (Channel ch : channels) {
                    if (!ch.isActive()) continue;
                    ch.writeAndFlush(buffer.retainedDuplicate(),ch.voidPromise());
                }
            }finally {
                buffer.release();
            }
        }

    }
}
