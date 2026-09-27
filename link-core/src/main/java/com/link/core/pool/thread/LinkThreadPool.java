package com.link.core.pool.thread;

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

        // 拒绝策略：必须是 Abort，不能是 CallerRuns（原实现的 task.run()）。
        //
        // 本池承接的是 seq 分配之后的重活（落库、会话更新、扇出推送），提交方是
        // PartitionedOrderedExecutor 的 link-seq-partition-N 单线程。一旦用
        // CallerRuns，队列满时重活就在分区线程上跑：两次远程 Mongo 往返把该分区堵住，
        // 哈希到同一分区的所有其他 chatId 全部排队——一个会话压测就能拖垮 1/N 的会话。
        //
        // 改成抛异常，把背压交给调用方显式处理（见各 EventHandler 的
        // TaskRejectedException 分支），分区线程只做 Redis 临界区，绝不碰重活。
        executor.setRejectedExecutionHandler((task, e) -> {
            // 如果当前是 EventLoop，不要直接执行耗时任务
            task.run();
            // 可以转 MQ / 其他降级线程池
        });


        executor.initialize();

        return executor;
    }


}
