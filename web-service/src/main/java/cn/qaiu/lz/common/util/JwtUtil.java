package cn.qaiu.lz.common.util;

import cn.qaiu.lz.web.model.SysUser;
import io.vertx.core.json.JsonObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;

/**
 * JWT工具类，用于生成和验证JWT token
 */
public class JwtUtil {

    private static final long EXPIRE_TIME = 24 * 60 * 60 * 1000; // token过期时间，24小时
    private static final String SECRET_KEY = "netdisk-fast-download-jwt-secret-key"; // 密钥
    private static final String ALGORITHM = "HmacSHA256";

    /**
     * 生成JWT token
     *
     * @param user 用户信息
     * @return JWT token
     */
    public static String generateToken(SysUser user) {
        long expireTime = getExpireTime();

        // Header
        JsonObject header = new JsonObject()
                .put("alg", "HS256")
                .put("typ", "JWT");

        // Payload
        JsonObject payload = new JsonObject()
                .put("id", user.getId())
                .put("username", user.getUsername())
                .put("role", user.getRole())
                .put("exp", expireTime)
                .put("iat", System.currentTimeMillis())
                .put("iss", "netdisk-fast-download");

        // Base64 encode header and payload
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.encode().getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.encode().getBytes(StandardCharsets.UTF_8));

        // Create signature
        String signature = hmacSha256(encodedHeader + "." + encodedPayload, SECRET_KEY);

        // Combine to form JWT
        return encodedHeader + "." + encodedPayload + "." + signature;
    }

    /**
     * 使用HMAC-SHA256算法生成签名
     *
     * @param data 要签名的数据
     * @param key 密钥
     * @return 签名
     */
    private static String hmacSha256(String data, String key) {
        try {
            Mac sha256Hmac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            sha256Hmac.init(secretKey);
            byte[] signedBytes = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signedBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Error creating HMAC SHA256 signature", e);
        }
    }

    /**
     * 验证JWT token
     *
     * @param token JWT token
     * @return 如果token有效返回true，否则返回false
     */
    public static boolean validateToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            String encodedHeader = parts[0];
            String encodedPayload = parts[1];
            String signature = parts[2];

            // 验证签名
            String expectedSignature = hmacSha256(encodedHeader + "." + encodedPayload, SECRET_KEY);
            if (!expectedSignature.equals(signature)) {
                return false;
            }

            // 验证过期时间
            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            JsonObject payloadJson = new JsonObject(payload);
            long expTime = payloadJson.getLong("exp", 0L);

            return System.currentTimeMillis() < expTime;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从token中获取用户ID
     *
     * @param token JWT token
     * @return 用户ID
     */
    public static String getUserIdFromToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        // Base64解码
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        JsonObject jsonObject = new JsonObject(payload);
        return jsonObject.getString("id");
    }

    /**
     * 从token中获取用户名
     *
     * @param token JWT token
     * @return 用户名
     */
    public static String getUsernameFromToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        // Base64解码
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        JsonObject jsonObject = new JsonObject(payload);
        return jsonObject.getString("username");
    }

    /**
     * 从token中获取用户角色
     *
     * @param token JWT token
     * @return 用户角色
     */
    public static String getRoleFromToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        // Base64解码
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        JsonObject jsonObject = new JsonObject(payload);
        return jsonObject.getString("role");
    }

    /**
     * 获取过期时间
     *
     * @return 过期时间戳
     */
    private static long getExpireTime() {
        return System.currentTimeMillis() + EXPIRE_TIME;
    }

    /**
     * 将过期时间戳转换为LocalDateTime
     *
     * @param expireTime 过期时间戳
     * @return LocalDateTime
     */
    public static LocalDateTime getExpireTimeAsLocalDateTime(long expireTime) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(expireTime), ZoneId.systemDefault());
    }
}
