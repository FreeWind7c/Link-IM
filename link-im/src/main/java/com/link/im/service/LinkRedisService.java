package com.link.im.service;

import com.link.common.redis.RedisKeys;
import com.link.im.entity.chat.ChatSessionMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月30日
 */
@Component
public class LinkRedisService  {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    public Set<String> getChatMemberIds(String chatId){
        Set<String> memberIds = redisTemplate.opsForSet().members(RedisKeys.CHAT_SESSION_MEMBER+chatId);
        if (memberIds == null || memberIds.isEmpty())
        {
            Query eq = new Query(Criteria.where("chat_id").is(chatId).and("active").is(true));
            eq.fields().include("owner_id");
            List<ChatSessionMember> chatSessionMembers = this.mongoTemplate.find(eq, ChatSessionMember.class);
            if (chatSessionMembers == null || chatSessionMembers.isEmpty())
                return null;
            memberIds = chatSessionMembers.stream().map(item -> { return item.getOwnerId().toString();
            }).collect(Collectors.toSet());
            redisTemplate.opsForSet().add(RedisKeys.CHAT_SESSION_MEMBER+chatId
                    ,memberIds.toArray(new String[0]));
        }
        return memberIds;
    }

    /**
     * 取某用户某一端当前有效的 token（登录时由 UserInfoService.userAuth 写入）。
     * 返回 null 表示该端从未登录过、或登录态已被清除。
     */
    public String getUserToken(String userId, int platform) {
        Object token = redisTemplate.opsForHash().get(RedisKeys.USER_TOKEN + userId, String.valueOf(platform));
        return token == null ? null : token.toString();
    }

}
