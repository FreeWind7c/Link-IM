package com.link.core.util.seq;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 *
 * 消息去重 + 会话级 seq 分配。GET 去重 / INCR 分配 / SET 标记三步在
 * Redis 内通过 Lua 脚本原子执行（见 resources/lua/dedup_seq.lua）。
 *
 * 用 StringRedisTemplate 执行：key/value/arg 全为纯 String，
 * 避免 ARGV 被 JSON 序列化加引号导致 Lua tonumber 失败。
 */
@Component
public class MessageSeqAllocator {

    /** 去重 key 的存活时间（秒），覆盖重发可能发生的时间窗口即可。这里给 1 天。 */
    private static final String DEDUP_TTL_SECONDS = "86400";

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisScript<List> dedupSeqScript;

    public MessageSeqAllocator(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/dedup_seq.lua")));
        // Lua 返回 {flag, seq} 数组 → 用 List 接收
        script.setResultType(List.class);
        this.dedupSeqScript = script;
    }

    /**
     * 为一条消息分配 seq；若是重发，返回它当初已分配的 seq（不重复递增）。
     *
     * @param conversationId 会话 ID
     * @param messageId      前端生成的 clientMsgId，全局唯一、重发时不变
     * @return SeqResult：duplicate 标识 + seq（无论新老，seq 都有值，回 ACK 用）
     */
    public SeqResult allocate(String conversationId, String messageId) {
        String dedupKey = "dedup:" + conversationId + ":" + messageId;
        String seqKey = "seq:" + conversationId;

        @SuppressWarnings("unchecked")
        List<Long> result = this.stringRedisTemplate.execute(
                this.dedupSeqScript,
                Arrays.asList(dedupKey, seqKey),   // KEYS[1], KEYS[2]
                DEDUP_TTL_SECONDS);                 // ARGV[1]

        long flag = result.get(0);
        long seq = result.get(1);
        return new SeqResult(flag == 1, seq);
    }

    /**
     * @param duplicate true 表示是重发（调用方应跳过入库，但仍要回 ACK）
     * @param seq       该消息对应的会话内 seq
     */
    public record SeqResult(boolean duplicate, long seq) {
    }
}
