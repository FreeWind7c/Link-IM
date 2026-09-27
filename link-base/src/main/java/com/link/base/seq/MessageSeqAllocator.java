package com.link.base.seq;

import org.springframework.core.io.ClassPathResource;
import com.link.common.redis.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.Arrays;
import java.util.List;


public class MessageSeqAllocator {

    private static final String DEDUP_TTL_SECONDS = "86400";

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisScript<List> dedupSeqScript;

    public static final String DEDUP = "dedup:";

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
     * 获取 StringRedisTemplate 实例，供外部清除去重标记使用
     */
    public StringRedisTemplate getStringRedisTemplate() {
        return stringRedisTemplate;
    }



    /**
     * 为一条消息分配 seq；若是重发，返回它当初已分配的 seq（不重复递增）。
     *
     * @param conversationId 会话 ID
     * @param messageId      前端生成的 clientMsgId，全局唯一、重发时不变
     * @return SeqResult：duplicate 标识 + seq（无论新老，seq 都有值，回 ACK 用）
     */
    public SeqResult allocate(String conversationId, String messageId) {
        String dedupKey = DEDUP + conversationId + ":" + messageId;
        String seqKey = RedisKeys.SEQ + conversationId;

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
     * 查询某会话当前已分配到的最新 seq（不递增）。
     *
     * @param chatId 会话 ID
     * @return 当前最新 seq；该会话还未分配过任何 seq 时返回 0
     */
    public long getCurrentSeq(String chatId) {
        String seqKey = RedisKeys.SEQ + chatId;
        String value = this.stringRedisTemplate.opsForValue().get(seqKey);
        return value == null ? 0L : Long.parseLong(value);
    }

    public long getMessageSeq(String chatId,String messageId){
        String value = (String) ( this.stringRedisTemplate.opsForValue().get(DEDUP + chatId + ":" + messageId));
        return value == null ? 0L : Long.parseLong(value);
    }

    /**
     * 清除去重标记，让同一条消息的重发能被当成新消息重新处理一遍。
     *
     * <p>用于「seq 已分配但重活没落地」的场合：线程池过载丢弃、落库异常等。
     * 不清的话客户端重发会命中 duplicate 分支，永远走不进落库路径。
     *
     * <p>代价是这次分配掉的 seq 成了空洞（重发会拿到新 seq）。会话内 seq 只要求单调递增、
     * 不要求连续，客户端按 seq 区间拉取不受影响。
     */
    public void releaseDedup(String chatId, String messageId) {
        this.stringRedisTemplate.delete(DEDUP + chatId + ":" + messageId);
    }

    /**
     * @param duplicate true 表示是重发（调用方应跳过入库，但仍要回 ACK）
     * @param seq       该消息对应的会话内 seq
     */
    public record SeqResult(boolean duplicate, long seq) {
    }
}
