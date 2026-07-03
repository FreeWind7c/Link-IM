package com.link.core.pool.thread;

import com.link.core.config.LinkCoreConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月02日
 *
 * 分区单线程池：固定 N 个单线程 executor，按 key 哈希取模路由到其中一个。
 *
 * <p>用途是"进程内有序分区"——同一个 key（如 chatId）恒定落到同一个单线程 executor，
 * 保证该 key 的任务严格串行、按提交顺序执行；不同 key 分散到不同 executor 并行，
 * 兼顾顺序性与吞吐。等价于 MQ 的"分区队列 + 单线程消费"，但无需引入 MQ。
 *
 * <p>只承担"必须有序"的极小临界区（如 seq 分配）；重活（落库、扇出推送）应在
 * 分区任务里再甩给共享线程池并行，避免单分区被慢 IO 拖成瓶颈。
 *
 * <p>队列无界：临界区只有一次 Redis INCR，排空极快，堆积风险低；且单线程 FIFO
 * 不能用 CallerRuns 兜底（会在提交线程直接执行，破坏顺序），故不设有界拒绝。
 */
@Slf4j
@Component
public class PartitionedOrderedExecutor {

    @Autowired
    private LinkCoreConfig config;

    /** N 个单线程 executor，下标即分区号。 */
    private ExecutorService[] partitions;

    /** 实际分区数，构造后固定不变（中途变更会破坏 key→分区 的映射，导致乱序）。 */
    private int partitionCount;

    @PostConstruct
    public void init() {
        this.partitionCount = Math.max(1, this.config.getSeqPartitionCount());
        this.partitions = new ExecutorService[this.partitionCount];
        for (int i = 0; i < this.partitionCount; i++) {
            final int idx = i;
            ThreadFactory tf = new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "link-seq-partition-" + idx + "-" + n.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            };
            this.partitions[i] = Executors.newSingleThreadExecutor(tf);
        }
        log.info("分区有序线程池初始化完成, 分区数={}", this.partitionCount);
    }

    /**
     * 按 key 哈希路由到固定分区并提交任务。同一 key 永远进同一分区 → 串行有序。
     *
     * @param key  分区键（如 chatId）；为 null 时退化到 0 号分区
     * @param task 要串行执行的任务
     */
    public void submit(String key, Runnable task) {
        int idx = partitionIndex(key);
        this.partitions[idx].execute(task);
    }

    /** key → 分区号：floorMod 保证负 hash 也落在 [0, N)。 */
    private int partitionIndex(String key) {
        if (key == null) {
            return 0;
        }
        return Math.floorMod(key.hashCode(), this.partitionCount);
    }

    @PreDestroy
    public void shutdown() {
        if (this.partitions == null) {
            return;
        }
        for (ExecutorService p : this.partitions) {
            p.shutdown();
        }
        for (ExecutorService p : this.partitions) {
            try {
                if (!p.awaitTermination(5, TimeUnit.SECONDS)) {
                    p.shutdownNow();
                }
            } catch (InterruptedException e) {
                p.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("分区有序线程池已关闭");
    }
}
