package com.link.base.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


@Slf4j
public class BasePlatFormRedisService {

    @Autowired
    public RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    public StringRedisTemplate stringRedisTemplate;

    // ==================== String/Value 操作 ====================

    public void setObject(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void setObject(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Object getObject(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Boolean deleteKey(String key) {
        return redisTemplate.delete(key);
    }

    public Long deleteKeys(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    public Boolean expireKey(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    public Long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    // ==================== Hash 操作 ====================

    public void putHash(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    public void putAllHash(String key, Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    public Object getHash(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    public Map<Object, Object> getAllHash(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public Boolean hasHashKey(String key, String field) {
        return redisTemplate.opsForHash().hasKey(key, field);
    }

    public Long deleteHash(String key, Object... fields) {
        return redisTemplate.opsForHash().delete(key, fields);
    }

    public Long getHashSize(String key) {
        return redisTemplate.opsForHash().size(key);
    }

    // ==================== List 操作 ====================

    public Long addListLeft(String key, Object value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    public Long addListRight(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    public Long addListAll(String key, Collection<Object> values) {
        return redisTemplate.opsForList().rightPushAll(key, values);
    }

    public Object popListLeft(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    public Object popListRight(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    public List<Object> getListRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    public List<Object> getListAll(String key) {
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    public Long listRightPushAll(String key, Object... obj) {
        return redisTemplate.opsForList().rightPushAll(key, obj);
    }

    public Long getListSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    public void trimList(String key, long start, long end) {
        redisTemplate.opsForList().trim(key, start, end);
    }

    // ==================== Set 操作 ====================

    public Long addSet(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    public Set getSetMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public Boolean isSetMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    public Long removeSet(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    public Long getSetSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    public Object popSet(String key) {
        return redisTemplate.opsForSet().pop(key);
    }

    // ==================== ZSet 操作 ====================

    public Boolean zAdd(String key, Object value, double score) {
        return redisTemplate.opsForZSet().add(key, value, score);
    }

    public Set<Object> zRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    public Set<Object> zRangeByScore(String key, double min, double max) {
        return redisTemplate.opsForZSet().rangeByScore(key, min, max);
    }

    public Long zRemove(String key, Object... values) {
        return redisTemplate.opsForZSet().remove(key, values);
    }

    public Long zSize(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    public Double zScore(String key, Object value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

    // ==================== 工具方法 ====================

    public RedisTemplate<String, Object> getRedisTemplate() {
        return this.redisTemplate;
    }

    public StringRedisTemplate getStringRedisTemplate() {
        return this.stringRedisTemplate;
    }

    public ValueOperations<String, Object> opsForValue() {
        return this.redisTemplate.opsForValue();
    }
}
