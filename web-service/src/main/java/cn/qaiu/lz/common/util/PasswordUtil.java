package cn.qaiu.lz.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码加密工具类
 * 使用SHA-256算法加盐进行密码加密和验证
 */
public class PasswordUtil {

    private static final String ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16; // 盐的长度
    private static final String DELIMITER = ":"; // 用于分隔盐和哈希值的分隔符

    /**
     * 对密码进行加密
     *
     * @param plainPassword 明文密码
     * @return 加密后的密码（格式：salt:hash）
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        try {
            // 生成随机盐
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            // 计算哈希值
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            md.update(salt);
            byte[] hashedPassword = md.digest(plainPassword.getBytes(StandardCharsets.UTF_8));

            // 将盐和哈希值编码为Base64并拼接
            String saltBase64 = Base64.getEncoder().encodeToString(salt);
            String hashBase64 = Base64.getEncoder().encodeToString(hashedPassword);

            // 返回格式：salt:hash
            return saltBase64 + DELIMITER + hashBase64;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("加密算法不可用", e);
        }
    }

    /**
     * 验证密码是否正确
     *
     * @param plainPassword 明文密码
     * @param hashedPassword 加密后的密码（格式：salt:hash）
     * @return 如果密码匹配返回true，否则返回false
     */
    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null || hashedPassword.isEmpty()) {
            return false;
        }

        try {
            // 分割盐和哈希值
            String[] parts = hashedPassword.split(DELIMITER);
            if (parts.length != 2) {
                return false;
            }

            String saltBase64 = parts[0];
            String hashBase64 = parts[1];

            // 解码盐
            byte[] salt = Base64.getDecoder().decode(saltBase64);

            // 使用相同的盐计算哈希值
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            md.update(salt);
            byte[] calculatedHash = md.digest(plainPassword.getBytes(StandardCharsets.UTF_8));
            String calculatedHashBase64 = Base64.getEncoder().encodeToString(calculatedHash);

            // 比较计算出的哈希值和存储的哈希值
            return hashBase64.equals(calculatedHashBase64);
        } catch (Exception e) {
            // 如果发生异常（例如格式不正确），返回false
            return false;
        }
    }
}
