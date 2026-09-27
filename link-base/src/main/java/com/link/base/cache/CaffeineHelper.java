package com.link.base.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine缓存工具类，提供类似Redis的操作接口
 * 依赖的Cache Bean由各模块自己配置提供
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月30日
 */
@Component
@RequiredArgsConstructor
public class CaffeineHelper {

    private final Cache<String, CacheValue> localCache;

    public void put(String key, Object value, long ttl, TimeUnit timeUnit) {
        CacheValue cacheValue = new CacheValue(value, ttl, timeUnit);
        localCache.put(key, cacheValue);
    }

    /**
     * 设置缓存，默认30分钟过期
     *
     * @param key   缓存key
     * @param value 缓存值
     */
    public void put(String key, Object value) {
        put(key, value, 30, TimeUnit.MINUTES);
    }

    public Object get(String key) {
        CacheValue cacheValue = localCache.getIfPresent(key);
        return cacheValue != null ? cacheValue.getValue() : null;
    }

    public <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * 删除缓存
     *
     * @param key 缓存key
     */
    public void delete(String key) {
        localCache.invalidate(key);
    }

    /**
     * 判断key是否存在
     *
     * @param key 缓存key
     * @return 是否存在
     */
    public boolean exists(String key) {
        return localCache.getIfPresent(key) != null;
    }

    /**
     * 按前缀批量删除缓存
     *
     * <p>用于 Change Stream resume token 丢失后的兜底全清：此时无法知道空窗期
     * 改了哪些文档，只能把这一类缓存整体作废。
     *
     * @param prefix key 前缀，如 {@code group_member:}
     * @return 实际删除的条目数
     */
    public int deleteByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return 0;
        }
        // 先收集再删除，避免遍历 keySet 的同时修改导致的并发问题
        java.util.List<String> matched = localCache.asMap().keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .toList();
        if (!matched.isEmpty()) {
            localCache.invalidateAll(matched);
        }
        return matched.size();
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        localCache.invalidateAll();
    }

    /**
     * 获取缓存大小
     *
     * @return 缓存条目数
     */
    public long size() {
        return localCache.estimatedSize();
    }

    /**
     * 获取缓存写入时间戳
     *
     * @param key 缓存key
     * @return 写入时间戳（毫秒），不存在返回null
     */
    public Long getTimestamp(String key) {
        CacheValue cacheValue = localCache.getIfPresent(key);
        return cacheValue != null ? cacheValue.getTimestamp() : null;
    }
}
