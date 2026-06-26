package com.link.im.processor.message;

import com.link.common.core.event.EventType;
import com.link.common.redis.RedisConstant;
import com.link.core.config.LinkCoreConfig;
import com.link.core.session.service.LinkSession;
import com.link.im.entity.chat.ChatMember;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.message.AbstractMessage;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.processor.LinkMessageProcessor;
import com.link.im.processor.borad.LinkGroupBroadcaster;
import io.netty.channel.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Component
public class LinkGroupMessageProcessor extends BasePlatFormMongoService<DefaultMessageInfo> implements LinkMessageProcessor {

    @Autowired
    private LinkCoreConfig config;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private LinkGroupBroadcaster broadcaster;

    @Override
    public void processor(AbstractMessage abstractMessage, Channel channel) {
        GroupMessageInfo message = (GroupMessageInfo) abstractMessage;
        Set<String> memberIds = redisTemplate.opsForSet().members(RedisConstant.GROUP_MEMBER+message.getChatId());
        if (memberIds == null || memberIds.isEmpty())
        {
                Query eq = eq(
                        where(col(ChatMember::getChatId)).is(message.getChatId())
                );
                eq.fields().include(col(ChatMember::getOwnerId));
                List<ChatMember> chatMembers = this.getMongoTemplate().find(eq, ChatMember.class);
                if (chatMembers == null || chatMembers.isEmpty())
                    return;
                memberIds = chatMembers.stream().map(item -> { return item.getOwnerId().toString();
                }).collect(Collectors.toSet());
                redisTemplate.opsForSet().add(RedisConstant.GROUP_MEMBER+message.getChatId()
                        ,memberIds.toArray(new String[0]));
        }

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

