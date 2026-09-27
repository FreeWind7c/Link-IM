package com.link.dispatcher.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.link.base.cache.CacheValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine缓存配置 - link-dispatcher模块
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月30日
 */
@Slf4j
@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<String, CacheValue> localCache() {

        Expiry<String, CacheValue> expiry = new Expiry<>() {

            @Override
            public long expireAfterCreate(
                    String key,
                    CacheValue value,
                    long currentTime) {

                // 使用包装类中的过期时间
                return value.getExpireTimeNanos();
            }

            @Override
            public long expireAfterUpdate(
                    String key,
                    CacheValue value,
                    long currentTime,
                    long currentDuration) {

                // 更新时使用新的过期时间
                return value.getExpireTimeNanos();
            }

            @Override
            public long expireAfterRead(
                    String key,
                    CacheValue value,
                    long currentTime,
                    long currentDuration) {

                // 读取不改变过期时间
                return currentDuration;
            }
        };

        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfter(expiry)
                .removalListener((String key, CacheValue value, RemovalCause cause) -> {

                    if (cause == RemovalCause.EXPIRED) {
                        log.info("缓存过期: key={}", key);
                    }

                })
                .build();
    }
}