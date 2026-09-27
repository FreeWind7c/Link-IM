package com.link.base.cache;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.concurrent.TimeUnit;

/**
 * 缓存值包装类，支持自定义过期时间
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月30日
 */
@Data
@AllArgsConstructor
public class CacheValue {
    private Object value;
    private long expireTimeNanos; // 过期时间（纳秒）
    private long createTime; // 创建时间（纳秒）
    private long timestamp; // 写入时间戳（毫秒），用于版本控制

    public CacheValue(Object value, long ttl, TimeUnit timeUnit) {
        this.value = value;
        this.expireTimeNanos = timeUnit.toNanos(ttl);
        this.createTime = System.nanoTime();
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 获取写入时间戳（毫秒）
     */
    public long getTimestamp() {
        return timestamp;
    }
}
