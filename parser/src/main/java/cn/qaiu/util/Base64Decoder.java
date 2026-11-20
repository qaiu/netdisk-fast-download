package cn.qaiu.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Strict Base64 decoding without the HotSpot Base64 intrinsic (CVE-2026-114938).
 * Like the JDK's String decoder, this reads ISO-8859-1 bytes.
 * Unmappable characters become '?', which is rejected by both alphabets.
 */
public final class Base64Decoder {
    private static final byte[] BASIC = alphabet("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/");
    private static final byte[] URL_SAFE = alphabet("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_");

    private Base64Decoder() {
    }

    public static byte[] decode(String input) {
        return decode(input.getBytes(StandardCharsets.ISO_8859_1), BASIC);
    }

    public static byte[] decodeUrl(String input) {
        return decode(input.getBytes(StandardCharsets.ISO_8859_1), URL_SAFE);
    }

    private static byte[] decode(byte[] input, byte[] alphabet) {
        int length = input.length;
        int dataLength = length;
        if (dataLength > 0 && input[dataLength - 1] == '=') {
            dataLength--;
            if (dataLength > 0 && input[dataLength - 1] == '=') {
                dataLength--;
            }
        }

        int remainder = dataLength % 4;
        int padding = length - dataLength;
        if (remainder == 1 || (padding != 0 && remainder + padding != 4)) {
            throw new IllegalArgumentException("Invalid Base64 padding or length");
        }

        int groupEnd = dataLength - remainder;
        byte[] decoded = new byte[(groupEnd / 4) * 3 + (remainder == 0 ? 0 : remainder - 1)];
        int start = Base64Encoder.useVectors(groupEnd)
                ? VectorBase64.decode(input, groupEnd, decoded, alphabet == URL_SAFE) : 0;
        int outputIndex = start / 4 * 3;
        // Each complete group consumes exactly four characters and writes exactly three bytes.
        for (int i = start; i < groupEnd; i += 4) {
            int a = alphabet[input[i] & 0xff];
            int b = alphabet[input[i + 1] & 0xff];
            int c = alphabet[input[i + 2] & 0xff];
            int d = alphabet[input[i + 3] & 0xff];
            if ((a | b | c | d) < 0) {
                throw new IllegalArgumentException("Invalid Base64 character");
            }
            int bits = (a << 18) | (b << 12) | (c << 6) | d;
            decoded[outputIndex++] = (byte) (bits >>> 16);
            decoded[outputIndex++] = (byte) (bits >>> 8);
            decoded[outputIndex++] = (byte) bits;
        }

        // The two possible tails consume two or three characters and write one or two bytes.
        if (remainder != 0) {
            int a = alphabet[input[groupEnd] & 0xff];
            int b = alphabet[input[groupEnd + 1] & 0xff];
            int c = remainder == 3 ? alphabet[input[groupEnd + 2] & 0xff] : 0;
            if ((a | b | c) < 0) {
                throw new IllegalArgumentException("Invalid Base64 character");
            }
            decoded[outputIndex] = (byte) ((a << 2) | (b >>> 4));
            if (remainder == 3) {
                decoded[outputIndex + 1] = (byte) ((b << 4) | (c >>> 2));
            }
        }
        return decoded;
    }

    private static byte[] alphabet(String characters) {
        // Index with an unsigned byte. Every non-alphabet value, including padding, maps to -1.
        byte[] table = new byte[256];
        Arrays.fill(table, (byte) -1);
        for (int i = 0; i < characters.length(); i++) {
            table[characters.charAt(i)] = (byte) i;
        }
        return table;
    }
}
