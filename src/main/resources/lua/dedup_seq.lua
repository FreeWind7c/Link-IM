-- 消息去重 + 会话级 seq 分配（原子操作，Redis 单线程保证）
--
-- KEYS[1] = 去重标记 key，粒度到单条消息：dedup:{conversationId}:{messageId}
-- KEYS[2] = seq 计数器 key，粒度到会话：       seq:{conversationId}
-- ARGV[1] = 去重 key 的过期秒数（滑动窗口，覆盖重发可能发生的时间即可）
--
-- 返回一个二元数组 {flag, seq}：
--   flag = 0：新消息，seq 是本次 INCR 出来的新值 → 调用方需入库
--   flag = 1：重发，seq 是当初分配的老值       → 调用方跳过入库，但仍要回 ACK(带这个 seq)

local existed = redis.call('GET', KEYS[1])
if existed then
    return {1, tonumber(existed)}        -- 重发：标志 1 + 老 seq，不再 INCR
end

local seq = redis.call('INCR', KEYS[2])
redis.call('SET', KEYS[1], seq, 'EX', tonumber(ARGV[1]))
return {0, seq}                          -- 新消息：标志 0 + 新 seq
