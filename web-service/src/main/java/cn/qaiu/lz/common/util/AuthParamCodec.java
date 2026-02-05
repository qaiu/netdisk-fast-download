package cn.qaiu.lz.common.util;

import cn.qaiu.lz.web.model.AuthParam;
import cn.qaiu.util.AESUtils;
import cn.qaiu.vx.core.util.SharedDataUtil;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 认证参数编解码工具类
 * <p>
 * 编码流程: JSON -> AES加密 -> Base64编码 -> URL编码
 * 解码流程: URL解码 -> Base64解码 -> AES解密 -> JSON解析
 * </p>
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * @date 2026/2/5
 */
@Slf4j
public class AuthParamCodec {

    /**
     * 默认加密密钥（16位）
     */
    private static final String DEFAULT_ENCRYPT_KEY = "nfd_auth_key2026";

    /**
     * 配置中的密钥路径
     */
    private static final String CONFIG_KEY_PATH = "authEncryptKey";

    private AuthParamCodec() {
        // 工具类禁止实例化
    }

    /**
     * 获取加密密钥
     * 优先从配置文件读取，如果未配置则使用默认密钥
     */
    public static String getEncryptKey() {
        try {
            String configKey = SharedDataUtil.getJsonStringForServerConfig(CONFIG_KEY_PATH);
            if (StringUtils.isNotBlank(configKey)) {
                return configKey;
            }
        } catch (Exception e) {
            log.debug("从配置读取加密密钥失败，使用默认密钥: {}", e.getMessage());
        }
        return DEFAULT_ENCRYPT_KEY;
    }

    /**
     * 解码认证参数
     * 解码流程: URL解码 -> Base64解码 -> AES解密 -> JSON解析
     *
     * @param encryptedAuth 加密后的认证参数字符串
     * @return AuthParam 对象，解码失败返回 null
     */
    public static AuthParam decode(String encryptedAuth) {
        return decode(encryptedAuth, getEncryptKey());
    }

    /**
     * 解码认证参数（指定密钥）
     *
     * @param encryptedAuth 加密后的认证参数字符串
     * @param key           AES密钥（16位）
     * @return AuthParam 对象，解码失败返回 null
     */
    public static AuthParam decode(String encryptedAuth, String key) {
        if (StringUtils.isBlank(encryptedAuth)) {
            return null;
        }

        try {
            // Step 1: URL解码
            String urlDecoded = URLDecoder.decode(encryptedAuth, StandardCharsets.UTF_8);
            log.debug("URL解码结果: {}", urlDecoded);

            // Step 2: Base64解码
            byte[] base64Decoded = Base64.getDecoder().decode(urlDecoded);
            log.debug("Base64解码成功，长度: {}", base64Decoded.length);

            // Step 3: AES解密
            String jsonStr = AESUtils.decryptByAES(base64Decoded, key);
            log.debug("AES解密结果: {}", jsonStr);

            // Step 4: JSON解析
            JsonObject json = new JsonObject(jsonStr);
            AuthParam authParam = new AuthParam(json);
            log.info("认证参数解码成功: authType={}", authParam.getAuthType());
            return authParam;

        } catch (IllegalArgumentException e) {
            log.warn("认证参数Base64解码失败: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("认证参数解码失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 编码认证参数
     * 编码流程: JSON -> AES加密 -> Base64编码 -> URL编码
     *
     * @param authParam 认证参数对象
     * @return 加密后的字符串，编码失败返回 null
     */
    public static String encode(AuthParam authParam) {
        return encode(authParam, getEncryptKey());
    }

    /**
     * 编码认证参数（指定密钥）
     *
     * @param authParam 认证参数对象
     * @param key       AES密钥（16位）
     * @return 加密后的字符串，编码失败返回 null
     */
    public static String encode(AuthParam authParam, String key) {
        if (authParam == null || !authParam.hasValidAuth()) {
            return null;
        }

        try {
            // Step 1: 转换为JSON
            String jsonStr = authParam.toJsonObject().encode();
            log.debug("JSON字符串: {}", jsonStr);

            // Step 2: AES加密 + Base64编码
            String base64Encoded = AESUtils.encryptBase64ByAES(jsonStr, key);
            log.debug("AES+Base64编码结果: {}", base64Encoded);

            // Step 3: URL编码
            String urlEncoded = java.net.URLEncoder.encode(base64Encoded, StandardCharsets.UTF_8);
            log.debug("URL编码结果: {}", urlEncoded);

            return urlEncoded;

        } catch (Exception e) {
            log.error("认证参数编码失败: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 编码认证参数（从JsonObject）
     *
     * @param json 认证参数JSON对象
     * @return 加密后的字符串
     */
    public static String encode(JsonObject json) {
        return encode(new AuthParam(json));
    }

    /**
     * 编码认证参数（从JSON字符串）
     *
     * @param jsonStr 认证参数JSON字符串
     * @return 加密后的字符串
     */
    public static String encodeFromJsonString(String jsonStr) {
        if (StringUtils.isBlank(jsonStr)) {
            return null;
        }
        try {
            JsonObject json = new JsonObject(jsonStr);
            return encode(new AuthParam(json));
        } catch (Exception e) {
            log.error("JSON解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证加密的认证参数是否有效
     *
     * @param encryptedAuth 加密后的认证参数
     * @return true 如果可以成功解码
     */
    public static boolean isValid(String encryptedAuth) {
        return decode(encryptedAuth) != null;
    }

    /**
     * 快速构建并编码认证参数
     *
     * @param authType 认证类型
     * @param token    token/cookie/credential
     * @return 加密后的字符串
     */
    public static String quickEncode(String authType, String token) {
        AuthParam authParam = AuthParam.builder()
                .authType(authType)
                .token(token)
                .build();
        return encode(authParam);
    }

    /**
     * 快速构建并编码用户名密码认证
     *
     * @param username 用户名
     * @param password 密码
     * @return 加密后的字符串
     */
    public static String quickEncodePassword(String username, String password) {
        AuthParam authParam = AuthParam.builder()
                .authType("password")
                .username(username)
                .password(password)
                .build();
        return encode(authParam);
    }
}
