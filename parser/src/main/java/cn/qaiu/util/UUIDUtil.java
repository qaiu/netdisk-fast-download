package cn.qaiu.util;

import java.security.SecureRandom;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2024/5/13 4:10
 */
public class UUIDUtil {

    public static String fjUuid() {
        return generateRandomString(21);
    }

    public static String generateRandomString(int length) {
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[length];
        random.nextBytes(randomBytes);

        StringBuilder sb = new StringBuilder();
        for (byte b : randomBytes) {
            int value = b & 0x3F; // 63 in hexadecimal
            if (value < 36) {
                sb.append(Integer.toString(value, 36));
            } else if (value < 62) {
                sb.append(Character.toUpperCase(Integer.toString(value - 26, 36).charAt(0)));
            } else if (value > 62) {
                sb.append("-");
            } else { // value == 62 or 63
                sb.append("_");
            }
        }
        return sb.toString();
    }
}
