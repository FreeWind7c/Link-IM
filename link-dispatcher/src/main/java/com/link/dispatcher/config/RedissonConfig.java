package com.link.dispatcher.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {

        Config config = new Config();

        String address = "redis://175.178.245.39:6379";

        config.useSingleServer()
                .setAddress(address)
                .setPassword("K9mP#xL2@wT6yU5zB")
            ;


        return Redisson.create(config);
    }

}
