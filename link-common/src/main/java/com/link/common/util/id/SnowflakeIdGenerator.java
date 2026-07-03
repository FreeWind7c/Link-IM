package com.link.common.util.id;

/**
 * 雪花算法 ID 生成器（Twitter Snowflake）。
 * <p>
 * 64 位 long 结构：
 * <pre>
 * 0 | 41 位时间戳(毫秒) | 5 位数据中心 | 5 位机器 | 12 位序列号
 * </pre>
 * - 最高位固定 0，保证生成的 id 为正数。
 * - 时间戳相对自定义纪元，可用约 69 年。
 * - 数据中心 + 机器共 10 位，支持 1024 个节点。
 * - 序列号 12 位，单节点同一毫秒内可生成 4096 个 id。
 * <p>
 * 实例线程安全（synchronized）。分布式不重复的前提是各节点 dataCenterId/workerId 组合唯一。
 */
public class SnowflakeIdGenerator {

    /** 自定义起始纪元：2026-01-01 00:00:00 UTC（毫秒）。一旦上线不可修改。 */
    private static final long EPOCH = 1767225600000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATA_CENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);          // 31
    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS); // 31
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);            // 4095

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;                                  // 12
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;            // 17
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS; // 22

    private final long workerId;
    private final long dataCenterId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long dataCenterId, long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 必须在 0 ~ " + MAX_WORKER_ID + " 之间");
        }
        if (dataCenterId < 0 || dataCenterId > MAX_DATA_CENTER_ID) {
            throw new IllegalArgumentException("dataCenterId 必须在 0 ~ " + MAX_DATA_CENTER_ID + " 之间");
        }
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
    }

    public synchronized long nextId() {
        long timestamp = currentTime();

        if (timestamp < lastTimestamp) {
            // 时钟回拨：短暂回拨等待追平，长回拨直接抛异常，避免生成重复 id
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                timestamp = waitUntil(lastTimestamp);
            } else {
                throw new IllegalStateException("时钟回拨过大，拒绝生成 id，回拨毫秒数=" + offset);
            }
        }

        if (timestamp == lastTimestamp) {
            // 同一毫秒内递增序列号
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 序列号用尽，等待下一毫秒
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitUntil(long targetTimestamp) {
        long timestamp = currentTime();
        while (timestamp < targetTimestamp) {
            timestamp = currentTime();
        }
        return timestamp;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }
}
