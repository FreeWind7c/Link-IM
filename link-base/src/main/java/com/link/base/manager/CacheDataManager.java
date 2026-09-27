package com.link.base.manager;

import com.link.base.cache.CacheInvalidationPublisher;
import com.link.base.cache.CaffeineHelper;
import com.link.base.entity.group.GroupInfo;
import com.link.base.entity.group.GroupMember;
import com.link.base.redis.BasePlatFormRedisService;
import com.link.common.redis.RedisKeys;
import com.link.base.entity.chat.ChatSessionMember;
import com.link.base.mongo.BasePlatFormMongoService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 缓存数据管理器
 * 提供多级缓存（Caffeine + Redis）+ MongoDB的数据访问
 * 使用本地锁防止单机缓存击穿
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月30日
 */
@Slf4j
public class CacheDataManager extends BasePlatFormMongoService<Object> {

    @Autowired
    private CaffeineHelper cache;

    @Autowired
    private BasePlatFormRedisService redisService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    @Qualifier("primaryMongoTemplate")
    private MongoTemplate primaryMongoTemplate;

    @Autowired(required = false)
    private CacheInvalidationPublisher publisher;

    public final long EXPIRE_TIME = 60;

    public final TimeUnit TIME_UNIT = TimeUnit.MINUTES;

    private final ConcurrentHashMap<String, Object> localLocks = new ConcurrentHashMap<>();

    /**
     * 获取群成员ID集合
     *
     * @param groupId 群聊ID
     * @return 成员ID集合
     */
    public Set<String> getGroupMemberId(String groupId) {
        return getGroupMemberId(groupId, false);
    }

    /**
     * 获取群成员ID集合
     *
     * @param groupId 群聊ID
     * @param forceFromPrimary 是否强制从主节点读取（用于刚更新后立即查询的场景）
     * @return 成员ID集合
     */
    public Set<String> getGroupMemberId(String groupId, boolean forceFromPrimary) {
        String key = RedisKeys.GROUP_MEMBER + groupId;
        // 如果强制从主节点读取，跳过缓存直接查库
        if (forceFromPrimary) {
            log.info("1");
            return loadFromMongo(groupId, key);
        }

        // 1. 尝试从缓存获取（本地 → Redis）
        Set<String> cached = getFromCache(key, Set.class);
        if (cached != null) {
            return cached;
        }

        // 2. 缓存未命中，使用本地锁加载（防止单机并发打到DB）
        Object lock = localLocks.computeIfAbsent(key, k -> new Object());

        synchronized (lock) {
            try {
                // 双重检查：获取锁后再次检查缓存（除非强制从主节点读取）
                if (!forceFromPrimary) {
                    Set<String> doubleCheck = getFromCache(key, Set.class);
                    if (doubleCheck != null) {
                        return doubleCheck;
                    }
                }

                return loadFromMongo(groupId, key);

            } catch (Exception e) {
                log.error("加载群成员失败: {}", groupId, e);
                return Collections.emptySet();
            } finally {
                // 清理锁对象，防止内存泄漏
                localLocks.remove(key, lock);
            }
        }
    }

    /**
     * 从 MongoDB 加载群成员数据
     */
    private Set<String> loadFromMongo(String groupId, String key) {
        // 缓存确实为空，查询MongoDB
        log.info("缓存未命中，从MongoDB加载: {},时间：{}", groupId, ( LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        Query query = eq(where(col(GroupMember::getGroupId)).is(new ObjectId(groupId)));
        query.fields().include(col(GroupMember::getUserId));
        List<GroupMember> chatSessionMembers =
                mongo.find(query, GroupMember.class);

        Set<String> result = chatSessionMembers.stream()
                .map(item -> item.getUserId().toString())
                .collect(Collectors.toSet());

        // 写入两级缓存（包括空结果，防止缓存穿透）
        if (!result.isEmpty()) {
            redisService.addSet(key, result.toArray(new String[0]));
        }else {
            log.info("4");
            return result;
        }
        redisService.expireKey(key, EXPIRE_TIME, TIME_UNIT);
        cache.put(key, result, EXPIRE_TIME, TIME_UNIT);
        return result;
    }

    /**
     * 从缓存获取数据（本地 → Redis）
     *
     * @param key  缓存key
     * @param type 数据类型
     * @return 缓存数据，未命中返回null
     */
    private <T> T getFromCache(String key, Class<T> type) {
        // 1. 先查本地缓存
        T localCached = cache.get(key, type);
        if (localCached != null) {
            log.info("本地缓存命中: {},时间,{}", key,System.currentTimeMillis());
            return localCached;
        }

        // 2. 再查Redis缓存（需要根据type判断使用什么Redis数据结构）
        if (type == Set.class || Set.class.isAssignableFrom(type)) {
            Set<Object> redisCached = redisService.getSetMembers(key);
            if (redisCached != null && !redisCached.isEmpty()) {
                log.info("Redis缓存命中: {},时间,{}", key,System.currentTimeMillis());
                // 回写本地缓存
                cache.put(key, redisCached, EXPIRE_TIME, TIME_UNIT);
                return (T) redisCached;
            }
        } else {
            // 其他类型使用opsForValue
            Object redisCached = redisService.getObject(key);
            if (redisCached != null) {
                log.debug("Redis缓存命中: {},时间,{}", key,System.currentTimeMillis());
                // 回写本地缓存
                cache.put(key, redisCached, EXPIRE_TIME, TIME_UNIT);
                return (T) redisCached;
            }
        }

        return null;
    }

    /**
     * 获取用户Token
     *
     * @param userId   用户ID
     * @param platform 平台类型
     * @return Token字符串
     */
    public String getUserToken(String userId, int platform) {
        System.out.println("key:" + RedisKeys.USER_REFRESH_TOKEN + userId + ":" + platform);
        Object token = redisService.getObject(RedisKeys.USER_REFRESH_TOKEN + userId+":"+platform);
        return token == null ? null : token.toString();
    }

    /**
     * 移除缓存
     *
     * @param key 缓存key
     */
    public void removeCache(String key) {

        System.out.println("[缓存管理器]删除key:" + key);
        // 1. 删除 Redis 缓存
        cache.delete(key);

        Boolean deleteResult = redisService.deleteKey(key);
        log.info("删除: {}，时间,{}", key, ( LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        if (publisher != null) {
            publisher.publish(key);
        }

    }
}
