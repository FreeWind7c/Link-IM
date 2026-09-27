package com.link.base.cache;

import com.link.common.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于Redis Streams的缓存失效发布器
 * 提供可靠的消息投递保证，替代原有的Pub/Sub机制
 *
 * Redis Streams特性：
 * 1. 消息持久化：写入Stream后不会因为消费者离线而丢失
 * 2. 消费者组：支持多个节点作为消费者，节点重启后可继续消费
 * 3. ACK机制：消费者必须确认处理，否则消息会重新投递
 * 4. Pending List：未ACK的消息会记录，节点恢复后可继续处理
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月31日
 */
@Slf4j
@RequiredArgsConstructor
public class CacheInvalidationPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    public void publish(String... keys) {
        if (keys == null || keys.length == 0) {
            log.warn("发布缓存失效消息时，keys 为空");
            return;
        }

        for (String key : keys) {
            try {
                // 构建消息体
                Map<String, Object> message = new HashMap<>();
                message.put("key", key);
                message.put("timestamp", System.currentTimeMillis());
                message.put("operation", "DELETE");

                // 写入Stream（持久化到Redis）
                ObjectRecord<String, Map<String, Object>> record = StreamRecords
                        .newRecord()
                        .ofObject(message)
                        .withStreamKey(RedisKeys.CACHE_INVALIDATION_STREAM);

                String messageId = redisTemplate.opsForStream()
                        .add(record)
                        .getValue();

            } catch (Exception e) {
                log.error("发布缓存失效消息到Stream失败，key: {}", key, e);
                // 即使发布失败，也不影响业务流程（缓存已在Redis层删除）
            }
        }
    }
}
