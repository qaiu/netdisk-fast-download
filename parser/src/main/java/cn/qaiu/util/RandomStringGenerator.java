package cn.qaiu.util;

import java.security.SecureRandom;
import java.util.UUID;

public class RandomStringGenerator {
    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH = 13; // 每段长度为13

    public static String generateRandomString() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 2; i++) { // 生成两段
            for (int j = 0; j < LENGTH; j++) {
                int index = random.nextInt(CHARACTERS.length());
                sb.append(CHARACTERS.charAt(index));
            }
        }

        return sb.toString();
    }

        public static String gen36String() {
            String uuid = UUID.randomUUID().toString().toLowerCase();
            // 移除短横线
            return uuid.replace("-", "");
        }
}
