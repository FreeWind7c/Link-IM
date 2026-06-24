package com.link.im.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class MD5Util {

    /**
     * MD5加密
     */
    public static String encrypt(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            byte[] digest = md.digest(
                    text.getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder sb = new StringBuilder();

            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xff);

                if (hex.length() == 1) {
                    sb.append("0");
                }

                sb.append(hex);
            }

            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("MD5加密失败", e);
        }
    }

    /**
     * 验证MD5
     */
    public static boolean verify(String text, String md5) {
        return encrypt(text).equalsIgnoreCase(md5);
    }

    /**
     * MD5不可逆，无法解密
     */
    public static String decrypt(String md5) {
        throw new UnsupportedOperationException(
                "MD5是不可逆摘要算法，不支持解密"
        );
    }

}