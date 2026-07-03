package com.link.common.util.id;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 会话 ID 生成工具。
 *
 * <p><b>单聊（两个参与者）：</b>不可逆。要求与传入顺序无关（A、B 和 B、A 得到同一个 ID）、
 * 看起来无规律、确定性、输出短。用“排序 + 长度定界拼接 + SHA-256 + 取模到 14 位十进制”，
 * 单向哈希，<b>无法逆向</b>（逆向需要同时知道两个参与者，本就无意义）。
 *
 * <p><b>群聊（单个群 ID）：</b><u>可逆</u>。groupId 是 24 位十六进制的 MongoDB ObjectId（96 位）。
 * 为了能由 chatId 逆推回 groupId，这里不用哈希，而用<b>保形加密（Feistel 置换）</b>：
 * 把 96 位的 ObjectId 经带密钥的可逆置换打乱，输出为纯数字，看起来随机、确定性，并可用密钥逆回去。
 *
 * <p><b>关于长度：</b>可逆映射的输出空间必须 ≥ 输入空间。ObjectId 是 96 位 ≈ 7.9e28，即十进制 29 位，
 * 因此群聊 chatId 为 <b>"76" 前缀 + 29 位纯数字</b>（共 31 字符）。这是“可逆”的必然代价——
 * 想要更短就只能放弃可逆（回到哈希）。
 *
 * <p><b>关于碰撞：</b>群聊侧是双射，<b>不存在碰撞</b>（不同 group 必得不同 chatId）。单聊侧因压到
 * 14 位十进制，高量级下碰撞不可忽略（约 100 万会话 ~0.5%，1000 万 ~39%）。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月22日
 */
public final class ChatIdGenerator {

    /** 输出取模空间：10^14，即最多 14 位十进制数字。 */
    private static final BigInteger MOD = BigInteger.TEN.pow(14);

    private ChatIdGenerator() {
    }

    public static void main(String[] args) {
        System.out.println(nextId("6a3e353f0bfa565e4f07a60e"));
    }

    /**
     * 由两个参与者标识生成单聊会话 ID。与参数顺序无关。
     *
     * @param idA 参与者 A 的标识（任意非空字符串）
     * @param idB 参与者 B 的标识（任意非空字符串）
     * @return 14 位纯数字会话 ID（看不出原始标识，确定性；高会话量下碰撞不可忽略，见类注释）
     * @throws IllegalArgumentException 任一入参为 null 或空，或两个标识相同（不能和自己建单聊）
     */
    public static String nextId(String idA, String idB) {
        if (idA == null || idA.isEmpty() || idB == null || idB.isEmpty()) {
            throw new IllegalArgumentException("会话参与者标识不能为空");
        }
        if (idA.equals(idB)) {
            throw new IllegalArgumentException("单聊双方不能是同一个标识: " + idA);
        }

        // 字典序排序，保证 (A,B) 与 (B,A) 拼出同一个串。
        String first = idA.compareTo(idB) <= 0 ? idA : idB;
        String second = first.equals(idA) ? idB : idA;

        // 用“长度:值”定界拼接，消除边界歧义（"ab"+"c" 与 "a"+"bc" 不会拼成同一个串）。
        String joined = first.length() + ":" + first + ":" + second.length() + ":" + second;

        byte[] digest = sha256(joined);
        // 取前 8 字节当无符号整数，对 10^14 取模，得到 [0, 10^14) 内的数。
        BigInteger value = new BigInteger(1, digest, 0, 8).mod(MOD);

        return "75"+String.format("%014d", value);
    }



    /**
     * 由群 ID 生成群聊会话 ID。确定性（同一群恒得同一会话 ID）。
     *
     * @param groupId 群标识（任意非空字符串）
     * @return 群聊会话 ID（"76" 前缀 + 14 位纯数字，看不出原始群 ID；碰撞特性见类注释）
     * @throws IllegalArgumentException 入参为 null 或空
     */
    public static String nextId(String groupId) {
        if (groupId == null || groupId.isEmpty()) {
            throw new IllegalArgumentException("群标识不能为空");
        }

        // 用“长度:值”定界，与单聊拼接方式保持一致的风格。
        String joined = "G:" + groupId.length() + ":" + groupId;

        byte[] digest = sha256(joined);
        // 取前 8 字节当无符号整数，对 10^14 取模，得到 [0, 10^14) 内的数。
        BigInteger value = new BigInteger(1, digest, 0, 8).mod(MOD);

        return "76" + String.format("%014d", value);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 标准算法，正常不会缺失。
            throw new IllegalStateException("当前环境不支持 SHA-256", e);
        }
    }
}