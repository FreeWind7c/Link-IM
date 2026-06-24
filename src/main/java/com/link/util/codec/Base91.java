package com.link.util.codec;

import java.nio.ByteBuffer;

public class Base91 {
    private static final float AVERAGE_ENCODING_RATIO = 1.2297f;
    public static final byte[] ENCODING_TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$%&()*+,./:;'=\\?@[]^_`{-}~\"".getBytes();
    private static final int BASE = ENCODING_TABLE.length;
    private static final byte[] DECODING_TABLE = new byte[256];

    static {
        for (int i = 0; i < 256; i++) {
            DECODING_TABLE[i] = -1;
        }
        for (int i2 = 0; i2 < BASE; i2++) {
            DECODING_TABLE[ENCODING_TABLE[i2]] = (byte) i2;
        }
    }

    public static String encodeToString(byte[] data) {
        return new String(encodeBytes(data));
    }

    public static byte[] encode(byte[] data) {
        return encodeBytes(data);
    }

    private static byte[] encodeBytes(byte[] data) {
        int estimatedSize = (int) Math.ceil(data.length * AVERAGE_ENCODING_RATIO);
        ByteBuffer output = ByteBuffer.allocate(estimatedSize);
        int ebq = 0;
        int en = 0;
        for (byte b : data) {
            ebq |= (b & 255) << en;
            en += 8;
            if (en > 13) {
                int ev = ebq & 8191;
                if (ev > 88) {
                    ebq >>= 13;
                    en -= 13;
                } else {
                    ev = ebq & 16383;
                    ebq >>= 14;
                    en -= 14;
                }
                output.put(ENCODING_TABLE[ev % BASE]);
                output.put(ENCODING_TABLE[ev / BASE]);
            }
        }
        if (en > 0) {
            output.put(ENCODING_TABLE[ebq % BASE]);
            if (en > 7 || ebq > 90) {
                output.put(ENCODING_TABLE[ebq / BASE]);
            }
        }
        return output.array();
    }

    public static byte[] decode(String dataString) {
        return decode(dataString.getBytes());
    }

    public static byte[] decode(byte[] data) {
        int dbq = 0;
        int dn = 0;
        int dv = -1;
        int estimatedSize = Math.round(data.length / AVERAGE_ENCODING_RATIO);
        ByteBuffer output = ByteBuffer.allocate(estimatedSize);
        for (int i = 0; i < data.length; i++) {
            if (DECODING_TABLE[data[i]] != -1) {
                if (dv == -1) {
                    dv = DECODING_TABLE[data[i]];
                } else {
                    int dv2 = dv + (DECODING_TABLE[data[i]] * BASE);
                    dbq |= dv2 << dn;
                    dn += (dv2 & 8191) > 88 ? 13 : 14;
                    do {
                        output.put((byte) dbq);
                        dbq >>= 8;
                        dn -= 8;
                    } while (dn > 7);
                    dv = -1;
                }
            }
        }
        if (dv != -1) {
            output.put((byte) (dbq | (dv << dn)));
        }
        return output.array();
    }
}