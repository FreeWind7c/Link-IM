package com.link.pool.thread;

import com.link.core.config.LinkCoreConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月15日
 */
@Slf4j
@Configuration
@EnableAsync
public class LinkThreadPool {

    @Autowired
    private LinkCoreConfig config;

    @Bean("IMExecutor")
    public ThreadPoolTaskExecutor imExecutor() {

        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        // 核心线程数
        executor.setCorePoolSize(this.config.getCorePoolSize());

        // 最大线程数
        executor.setMaxPoolSize(this.config.getMaxPoolSize());

        // 队列容量
        executor.setQueueCapacity(this.config.getQueueCapacity());

        // 空闲线程存活时间
        executor.setKeepAliveSeconds(this.config.getKeepAlive());

        // 线程名前缀
        executor.setThreadNamePrefix(this.config.getThreadNamePrefix());

        // 拒绝策略
        executor.setRejectedExecutionHandler((task,exec) -> {
            log.warn("IM线程池过载, 队列已满, activeCount={}", exec.getActiveCount());
        });


        executor.initialize();

        return executor;
    }

}
