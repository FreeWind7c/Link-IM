package com.link.dispatcher.processor.message;

import com.link.common.core.event.EventType;
import com.link.core.config.LinkCoreConfig;
import com.link.core.session.service.LinkSession;
import com.link.base.entity.base.BaseMessage;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.entity.message.GroupMessageInfo;
import com.link.base.manager.CacheDataManager;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.dispatcher.processor.LinkMessageProcessor;
import com.link.dispatcher.processor.borad.LinkGroupBroadcaster;
import io.netty.channel.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Component
public class LinkGroupMessageProcessor extends BasePlatFormMongoService<DefaultMessageInfo> implements LinkMessageProcessor {

    @Autowired
    private LinkCoreConfig config;

    @Autowired
    private CacheDataManager cacheManager;

    @Autowired
    private LinkGroupBroadcaster broadcaster;

    @Override
    public void processor(BaseMessage baseMessage, Channel channel) {
        GroupMessageInfo message = (GroupMessageInfo) baseMessage;
        long startTime = System.currentTimeMillis();
        Set<String> memberIds = this.cacheManager.getGroupMemberId(message.getRcvId().toHexString());
        System.out.println("time:" + (System.currentTimeMillis() - startTime));
        System.out.println("memberIds:" +memberIds.toString());
        ArrayList<Channel> channels = new ArrayList<>();
        for (String userId : memberIds) {
            if (userId.equals(message.getSndId()))
                continue;

            List<LinkSession> sessions = this.config.getSessionManager().getSession(userId);
            if (sessions == null)
                continue;

            sessions.forEach(session -> {
                Channel ch = session.getChannel();
                if (ch != null && ch.isActive())
                {
                    channels.add(ch);
                }
            });
        }

        if (channels.isEmpty())
            return;
        this.broadcaster.broadcast(EventType.GROUP_MESSAGE,channels,message);
    }
}

