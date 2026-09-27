package com.link.dispatcher.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.link.base.cache.*;
import com.link.base.facotry.LinkBaseDataFactory;
import com.link.base.facotry.LinkMessageFactory;
import com.link.base.manager.CacheDataManager;
import com.link.base.redis.BasePlatFormRedisService;
import com.link.base.repository.ChatSessionRepository;
import com.link.base.seq.MessageSeqAllocator;
import com.mongodb.client.MongoClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * link-base 模块的 Bean 配置
 * 显式注册 link-base 中需要的 Spring Bean
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月30日
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ChangeStreamProperties.class)
public class LinkBaseBeansConfig {

    /**
     * 消息序列号分配器
     */
    @Bean
    public MessageSeqAllocator messageSeqAllocator(StringRedisTemplate stringRedisTemplate) {
        return new MessageSeqAllocator(stringRedisTemplate);
    }

    /**
     * Redis 服务基类
     */
    @Bean
    public BasePlatFormRedisService basePlatFormRedisService() {
        return new BasePlatFormRedisService();
    }

    /**
     * 消息工厂
     */
    @Bean
    public LinkMessageFactory linkMessageFactory(
            MessageSeqAllocator seqAllocator,
            RedisTemplate redisTemplate) {
        return new LinkMessageFactory(seqAllocator, redisTemplate);
    }

    /**
     * 数据工厂
     */
    @Bean
    public LinkBaseDataFactory linkBaseDataFactory() {
        return new LinkBaseDataFactory();
    }

    /**
     * 会话仓储
     */
    @Bean
    public ChatSessionRepository chatSessionRepository() {
        return new ChatSessionRepository();
    }

    /**
     * 缓存失效发布器
     */
    @Bean
    public CacheInvalidationPublisher cacheInvalidationPublisher(
            RedisTemplate<String, Object> redisTemplate) {
        return new CacheInvalidationPublisher(redisTemplate);
    }

    @Bean
    public CacheDataManager restApiCacheDataManager() {
        return new CacheDataManager();
    }

    @Bean
    public CaffeineHelper caffeineHelper(Cache<String, CacheValue> cache) {
        return new CaffeineHelper(cache);
    }

    /**
     * 缓存失效监听器（基于Redis Streams）
     */
    @Bean
    public CacheInvalidationListener cacheInvalidationListener(
            CaffeineHelper caffeineHelper,
            RedisTemplate<String, Object> redisTemplate) {
        return new CacheInvalidationListener(caffeineHelper, redisTemplate);
    }

    /**
     * group_member 集合的缓存 key 解析器
     */
    @Bean
    public CacheKeyResolver groupMemberCacheKeyResolver() {
        return new GroupMemberCacheKeyResolver();
    }




}
