package cn.qaiu.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Base64Encoder {
    private static final boolean VECTORS = vectorsAvailable();
    static final String BASIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    static final String URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private Base64Encoder() {}

    private static boolean vectorsAvailable() {
        if (java.nio.ByteOrder.nativeOrder() != java.nio.ByteOrder.LITTLE_ENDIAN
                || ModuleLayer.boot().findModule("jdk.incubator.vector").isEmpty()) {
            return false;
        }
        try {
            return VectorBase64.available();
        } catch (LinkageError unavailableOnThisJdk) {
            return false;
        }
    }

    static boolean useVectors(int length) {
        return VECTORS && length >= 1024;
    }

    public static String encode(byte[] data) {
        return useVectors(data.length) ? new String(vectorEncode(data, false), StandardCharsets.ISO_8859_1)
                : Base64.getEncoder().encodeToString(data);
    }

    public static byte[] encodeBytes(byte[] data) {
        return useVectors(data.length) ? vectorEncode(data, false) : Base64.getEncoder().encode(data);
    }

    public static String encodeUrlWithoutPadding(byte[] data) {
        return useVectors(data.length) ? new String(vectorEncode(data, true), StandardCharsets.ISO_8859_1)
                : Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] vectorEncode(byte[] input, boolean url) {
        int remainder = input.length % 3;
        int tailLength = remainder + 1;
        if (remainder == 0) {
            tailLength = 0;
        } else if (!url) {
            tailLength = 4;
        }
        long length = (long) (input.length / 3) * 4 + tailLength;
        if (length > Integer.MAX_VALUE) {
            throw new OutOfMemoryError("Base64 output exceeds maximum array length");
        }
        byte[] output = new byte[(int) length];
        int src = VectorBase64.encode(input, output, url);
        int dst = src / 3 * 4;
        String alphabet = url ? URL : BASIC;
        while (src < input.length) {
            int bytes = Math.min(3, input.length - src);
            int bits = (input[src++] & 255) << 16;
            if (bytes >= 2) {
                bits |= (input[src++] & 255) << 8;
            }
            if (bytes == 3) {
                bits |= input[src++] & 255;
            }
            for (int shift = 18; shift >= 18 - bytes * 6; shift -= 6) {
                output[dst++] = (byte) alphabet.charAt((bits >>> shift) & 63);
            }
        }
        java.util.Arrays.fill(output, dst, output.length, (byte) '=');
        return output;
    }
}
