package cn.qaiu.util;

/** Strict Base64 decoding without the HotSpot Base64 intrinsic (CVE-2026-114938). */
public final class Base64Decoder {
    private static final String BASIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final String URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private Base64Decoder() {
    }

    public static byte[] decode(String input) {
        return decode(input, BASIC);
    }

    public static byte[] decodeUrl(String input) {
        return decode(input, URL_SAFE);
    }

    private static byte[] decode(String input, String alphabet) {
        int length = input.length();
        int firstPadding = input.indexOf('=');
        int dataLength = firstPadding < 0 ? length : firstPadding;
        int remainder = dataLength % 4;
        if (remainder == 1 || (firstPadding >= 0
                && (remainder == 0 || length % 4 != 0
                    || length - dataLength != 4 - remainder
                    || input.charAt(length - 1) != '='))) {
            throw new IllegalArgumentException("Invalid Base64 padding or length");
        }

        byte[] decoded = new byte[dataLength / 4 * 3 + remainder * 6 / 8];
        int buffer = 0;
        int bits = 0;
        int outputIndex = 0;
        for (int i = 0; i < dataLength; i++) {
            int value = alphabet.indexOf(input.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base64 character");
            }
            buffer = (buffer << 6) | value;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                decoded[outputIndex++] = (byte) (buffer >>> bits);
            }
        }
        return decoded;
    }
}
