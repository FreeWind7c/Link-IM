package com.link.base.cache;

import com.link.common.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;

/**
 * 基于Redis Streams的缓存失效监听器
 * 可靠消费缓存失效消息，保证本地缓存一致性
 *
 * 工作原理：
 * 1. 每个应用节点作为独立的消费者加入消费者组
 * 2. 消费者名称：应用名-主机名-进程ID（保证全局唯一）
 * 3. 启动时先处理Pending消息（未ACK的消息，可能是之前崩溃/重启遗留的）
 * 4. 然后持续消费新消息（阻塞等待）
 * 5. 消费成功后必须ACK，否则消息会保留在Pending列表
 *
 * 一致性保证：
 * - 节点重启：自动从上次ACK的位置继续消费，不会丢失消息
 * - 网络抖动：消息持久化在Redis中，网络恢复后继续消费
 * - 消费失败：未ACK的消息保留在Pending列表，下次启动时重新处理
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月31日
 */
@Slf4j
@RequiredArgsConstructor
public class CacheInvalidationListener implements InitializingBean, DisposableBean {

    private final CaffeineHelper caffeineHelper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${spring.application.name:link-app}")
    private String appName;

    private String consumerName;
    private Thread consumerThread;
    private volatile boolean running = false;

    @Override
    public void afterPropertiesSet() throws Exception {
        try {
            // 生成唯一的消费者名称（应用名+主机名+进程ID）
            String hostname = InetAddress.getLocalHost().getHostName();
            long pid = ProcessHandle.current().pid();
            consumerName = appName + "-" + hostname + "-" + pid;

            log.info("初始化缓存失效监听器，消费者名称: {}", consumerName);

            // 创建消费者组（如果不存在）
            createConsumerGroupIfNotExists();

            // 启动消费线程
            startConsuming();

        } catch (Exception e) {
            log.error("初始化缓存失效监听器失败", e);
        }
    }

    /**
     * 创建消费者组（幂等操作）
     */
    private void createConsumerGroupIfNotExists() {
        try {
            redisTemplate.opsForStream().createGroup(
                    RedisKeys.CACHE_INVALIDATION_STREAM,
                    RedisKeys.CACHE_INVALIDATION_GROUP
            );
            log.info("创建Redis Stream消费者组成功: {}", RedisKeys.CACHE_INVALIDATION_GROUP);
        } catch (Exception e) {
            // 消费者组已存在，忽略异常
            String message = e.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                log.debug("消费者组已存在: {}", RedisKeys.CACHE_INVALIDATION_GROUP);
            } else {
                log.warn("创建消费者组时发生异常", e);
            }
        }
    }

    /**
     * 启动消费线程（阻塞式消费）
     */
    private void startConsuming() {
        running = true;
        consumerThread = new Thread(() -> {
            log.info("开始消费Redis Stream，消费者: {}, Stream: {}",
                    consumerName, RedisKeys.CACHE_INVALIDATION_STREAM);

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    // 1. 先处理Pending消息（未ACK的消息，可能是之前崩溃/重启遗留的）
                    processPendingMessages();

                    // 2. 消费新消息（阻塞等待，超时5秒）
                    List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                            Consumer.from(RedisKeys.CACHE_INVALIDATION_GROUP, consumerName),
                            StreamReadOptions.empty()
                                    .count(10)  // 每次最多消费10条
                                    .block(Duration.ofSeconds(5)),
                            StreamOffset.create(RedisKeys.CACHE_INVALIDATION_STREAM, ReadOffset.lastConsumed())
                    );

                    if (messages != null && !messages.isEmpty()) {
                        for (MapRecord<String, Object, Object> message : messages) {
                            processMessage(message);
                        }
                    }

                } catch (Exception e) {
                    if (running) {
                        log.error("消费Redis Stream消息失败", e);
                        try {
                            Thread.sleep(1000);  // 失败后等待1秒重试
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            log.info("缓存失效消费线程已停止，消费者: {}", consumerName);
        }, "cache-invalidation-consumer-" + consumerName);

        consumerThread.setDaemon(false);  // 非守护线程，确保消息处理完成
        consumerThread.start();
    }

    /**
     * 处理Pending消息（重启后恢复未ACK的消息）
     * Pending消息是指已经被消费但未ACK的消息，可能原因：
     * 1. 消费者处理过程中崩溃
     * 2. 消费者处理过程中重启
     * 3. 消费者处理失败但未ACK
     */
    private void processPendingMessages() {
        try {
            // 查询当前消费者的Pending消息
            PendingMessages pending = redisTemplate.opsForStream().pending(
                    RedisKeys.CACHE_INVALIDATION_STREAM,
                    Consumer.from(RedisKeys.CACHE_INVALIDATION_GROUP, consumerName),
                    Range.unbounded(),
                    100L  // 最多获取100条pending
            );

            if (pending != null && pending.size() > 0) {
                log.info("检测到{}条未ACK的Pending消息，开始处理", pending.size());

                // 读取这些pending消息的详细内容
                for (PendingMessage pendingMessage : pending) {
                    String messageId = pendingMessage.getIdAsString();

                    // 使用XCLAIM命令认领这些消息
                    List<MapRecord<String, Object, Object>> claimedMessages = redisTemplate.opsForStream().claim(
                            RedisKeys.CACHE_INVALIDATION_STREAM,
                            RedisKeys.CACHE_INVALIDATION_GROUP,
                            consumerName,
                            Duration.ofMillis(0),  // 立即认领
                            RecordId.of(messageId)
                    );

                    if (claimedMessages != null && !claimedMessages.isEmpty()) {
                        for (MapRecord<String, Object, Object> message : claimedMessages) {
                            processMessage(message);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理Pending消息失败", e);
        }
    }

    /**
     * 处理单条消息
     */
    private void processMessage(MapRecord<String, Object, Object> message) {
        String messageId = message.getId().getValue();
        try {
            String key = (String) message.getValue().get("key");
            Object timestampObj = message.getValue().get("timestamp");
            Long timestamp = timestampObj instanceof Number ?
                    ((Number) timestampObj).longValue() : null;

            log.info("[Redis缓存管理器]收到缓存失效消息，key: {}, messageId: {}, timestamp: {}",
                    key, messageId, timestamp);

            if (key == null || key.isEmpty()) {
                // 脏消息：ACK 掉，否则它会一直卡在 Pending 列表里反复重试
                log.warn("缓存失效消息不含 key，直接 ACK，messageId: {}", messageId);
                acknowledge(messageId);
                return;
            }

            if (key.endsWith(MongoChangeStreamInvalidator.PREFIX_WILDCARD)) {
                // 前缀失效（Change Stream resume token 丢失后的兜底全清）
                String prefix = key.substring(0,
                        key.length() - MongoChangeStreamInvalidator.PREFIX_WILDCARD.length());
                int deleted = caffeineHelper.deleteByPrefix(prefix);
                log.warn("按前缀清理本地缓存，prefix: {}, 清理 {} 条", prefix, deleted);
            } else {
                // 删除本地缓存
                boolean existed = caffeineHelper.exists(key);
                caffeineHelper.delete(key);
            }

            // ACK消息（确认处理完成）
            acknowledge(messageId);

        } catch (Exception e) {
            log.error("处理缓存失效消息失败，messageId: {}", messageId, e);
        }
    }

    /**
     * ACK 消息，确认处理完成
     */
    private void acknowledge(String messageId) {
        redisTemplate.opsForStream().acknowledge(
                RedisKeys.CACHE_INVALIDATION_STREAM,
                RedisKeys.CACHE_INVALIDATION_GROUP,
                messageId
        );
        log.debug("消息已ACK: {}", messageId);
    }

    /**
     * 应用关闭时优雅停止消费线程
     */
    @Override
    public void destroy() throws Exception {
        log.info("开始停止缓存失效监听器，消费者: {}", consumerName);
        running = false;

        if (consumerThread != null && consumerThread.isAlive()) {
            try {
                consumerThread.interrupt();
                consumerThread.join(5000);  // 等待最多5秒
                log.info("缓存失效监听器已停止");
            } catch (InterruptedException e) {
                log.warn("等待消费线程停止时被中断", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
