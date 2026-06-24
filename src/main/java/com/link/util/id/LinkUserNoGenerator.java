package com.link.util.id;

import java.util.concurrent.ThreadLocalRandom;

/**
 * userNo 生成工具：纯数字、长度 4~8 位、随机。
 * <p>
 * 注意：本类只负责"生成随机号"，<b>不保证唯一</b>。
 * 唯一性需要在写库前查重 + 撞号重试，见 BaseMongoService#nextUnique。
 * <p>
 * 首位固定 1~9，不以 0 开头，避免当作 String 存储时出现 "0123" 这种歧义号。
 */
public class LinkUserNoGenerator {

    /** 最短位数 */
    public static final int MIN_LEN = 4;
    /** 最长位数 */
    public static final int MAX_LEN = 8;

    private LinkUserNoGenerator() {
    }

    /** 生成一个长度随机(4~8 位)的纯数字号 */
    public static String next() {
        int len = ThreadLocalRandom.current().nextInt(MIN_LEN, MAX_LEN + 1);
        return next(len);
    }

    /** 生成一个指定位数的纯数字号，位数必须在 4~8 之间 */
    public static String next(int len) {
        if (len < MIN_LEN || len > MAX_LEN) {
            throw new IllegalArgumentException("userNo 位数必须在 " + MIN_LEN + "~" + MAX_LEN + " 之间，传入=" + len);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(len);
        // 首位 1~9，避免前导 0
        sb.append(random.nextInt(1, 10));
        // 其余位 0~9
        for (int i = 1; i < len; i++) {
            sb.append(random.nextInt(0, 10));
        }
        return sb.toString();
    }
}
