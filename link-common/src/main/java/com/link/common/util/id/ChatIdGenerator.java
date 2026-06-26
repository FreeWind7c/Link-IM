package com.link.common.util.id;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 会话 ID 生成工具。
 *
 * <p>由两个参与者标识生成单聊会话 ID，要求：与传入顺序无关（A、B 和 B、A 得到同一个 ID）、
 * 看起来是无规律的随机数、确定性（同样输入永远得到同样输出）、输出为 14 位以内的纯数字。
 * 输入可以是任意字符串（含字母、任意长度，如 userNo、account 或 24 位 ObjectId 串），不做限制。
 *
 * <p><b>实现：排序 + 长度定界拼接 + SHA-256 + 取模到 14 位十进制。</b>先把两个标识按字典序
 * 排序（保证顺序无关），用“长度:值”方式拼接以消除边界歧义，做 SHA-256 后取前 8 字节为无符号
 * 整数，对 10^14 取模，左补零成 14 位数字。
 *
 * <p><b>关于碰撞（重要）：</b>输出被压到 14 位十进制（10^14 种取值），任意字符串映射到此空间，
 * 碰撞不可忽略。按生日悖论：约 100 万会话时碰撞概率约 0.5%，1000 万时约 39%。即用户/会话量
 * 达到百万级、尤其千万级时，<b>大概率出现两对不同的人算出同一会话 ID</b>，消息会串。这是
 * 输出压到 14 位的代价。若要“几乎绝不碰撞”，需放宽输出长度（如 64 位十六进制 SHA-256）。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月22日
 */
public final class ChatIdGenerator {

    /** 输出取模空间：10^14，即最多 14 位十进制数字。 */
    private static final BigInteger MOD = BigInteger.TEN.pow(14);

    private ChatIdGenerator() {
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
