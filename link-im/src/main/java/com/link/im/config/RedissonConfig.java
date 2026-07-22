package com.link.im.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.support.collections.RedisProperties;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {

        Config config = new Config();

        String address = "redis://127.0.0.1:6379";

        config.useSingleServer()
                .setAddress(address)
            ;


        return Redisson.create(config);
    }

}
