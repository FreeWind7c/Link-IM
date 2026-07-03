package com.link.common.util.id;

import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 全局 ID 工具类，基于雪花算法生成分布式唯一 id。
 * <p>
 * 直接静态调用：{@code LinkID.nextId()} / {@code LinkID.nextIdStr()}。
 * <p>
 * <b>分布式部署关键：</b>每个节点的 dataCenterId/workerId 组合必须唯一，否则可能重复。
 * 机器 id 的确定优先级：
 * <ol>
 *   <li>JVM 启动参数 {@code -Dsnowflake.dataCenterId=x -Dsnowflake.workerId=y}（推荐，最可控）</li>
 *   <li>未配置时，根据本机 MAC/IP 哈希自动推导（同网段下大概率不冲突，但不保证绝对唯一）</li>
 * </ol>
 * 多节点正式环境务必通过启动参数显式指定。
 */
public class LinkID {

    private static final SnowflakeIdGenerator GENERATOR;

    public static void main(String[] args) {
        System.out.println(nextIdStr());
    }


    static {
        long dataCenterId = resolveId("snowflake.dataCenterId", 31L);
        long workerId = resolveId("snowflake.workerId", 31L);

        if (dataCenterId < 0) {
            dataCenterId = autoId(0) & 31L;
        }
        if (workerId < 0) {
            workerId = autoId(1) & 31L;
        }
        GENERATOR = new SnowflakeIdGenerator(dataCenterId, workerId);
    }

    private LinkID() {
    }

    /** 生成 long 型 id */
    public static long nextId() {
        return GENERATOR.nextId();
    }

    /** 生成字符串型 id（实体 id 为 String 时用这个） */
    public static String nextIdStr() {
        return String.valueOf(GENERATOR.nextId());
    }

    /** 读取系统属性中的机器 id；未配置或非法返回 -1 表示走自动推导 */
    private static long resolveId(String key, long max) {
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) {
            return -1L;
        }
        try {
            long id = Long.parseLong(value.trim());
            return (id >= 0 && id <= max) ? id : -1L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** 根据本机网卡 MAC 地址哈希推导一个 id，salt 用于区分 dataCenterId / workerId */
    private static long autoId(int salt) {
        long hash = salt;
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                byte[] mac = nic.getHardwareAddress();
                if (mac != null) {
                    for (byte b : mac) {
                        hash = hash * 31 + (b & 0xff);
                    }
                }
            }
        } catch (Exception e) {
            // 拿不到网卡信息就退化为 salt，至少保证可运行
        }
        return Math.abs(hash);
    }
}
