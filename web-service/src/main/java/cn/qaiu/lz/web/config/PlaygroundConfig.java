package cn.qaiu.lz.web.config;

import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JS演练场配置
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Data
@Slf4j
public class PlaygroundConfig {

    /** Token有效期：24小时 */
    private static final long TOKEN_EXPIRY_MS = 24 * 60 * 60 * 1000L;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 单例实例
     */
    private static PlaygroundConfig instance;

    /**
     * 已颁发的认证Token及其创建时间
     */
    private final Map<String, Long> validTokens = new ConcurrentHashMap<>();
    
    /**
     * 是否启用演练场
     * 默认false，不启用
     */
    private boolean enabled = false;
    
    /**
     * 是否公开模式（不需要密码）
     * 默认false，需要密码访问
     */
    private boolean isPublic = false;
    
    /**
     * 访问密码
     * 默认密码：nfd_playground_2024
     */
    private String password = "nfd_playground_2024";
    
    /**
     * 私有构造函数
     */
    private PlaygroundConfig() {
    }

    /**
     * 生成并存储一个新的认证Token，同时清理过期Token
     */
    public String generateToken() {
        // 清理过期Token，防止Map无限增长
        long now = System.currentTimeMillis();
        validTokens.entrySet().removeIf(e -> now - e.getValue() > TOKEN_EXPIRY_MS);
        // 使用SecureRandom生成32字节的密码学安全Token
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        String token = sb.toString();
        validTokens.put(token, now);
        return token;
    }

    /**
     * 校验Token是否合法且未过期
     */
    public boolean validateToken(String token) {
        if (token == null || token.isEmpty()) return false;
        Long createdAt = validTokens.get(token);
        if (createdAt == null) return false;
        if (System.currentTimeMillis() - createdAt > TOKEN_EXPIRY_MS) {
            validTokens.remove(token);
            return false;
        }
        return true;
    }
    
    /**
     * 获取单例实例
     */
    public static PlaygroundConfig getInstance() {
        if (instance == null) {
            synchronized (PlaygroundConfig.class) {
                if (instance == null) {
                    instance = new PlaygroundConfig();
                }
            }
        }
        return instance;
    }
    
    /**
     * 从JsonObject加载配置
     */
    public static void loadFromJson(JsonObject config) {
        PlaygroundConfig cfg = getInstance();
        if (config != null && config.containsKey("playground")) {
            JsonObject playgroundConfig = config.getJsonObject("playground");
            cfg.enabled = playgroundConfig.getBoolean("enabled", false);
            cfg.isPublic = playgroundConfig.getBoolean("public", false);
            cfg.password = playgroundConfig.getString("password", "nfd_playground_2024");
            
            log.info("Playground配置已加载: enabled={}, public={}, password={}", 
                    cfg.enabled, cfg.isPublic, cfg.isPublic ? "N/A" : "已设置");
            
            if (!cfg.enabled) {
                log.info("演练场功能已禁用");
            } else if (!cfg.isPublic && "nfd_playground_2024".equals(cfg.password)) {
                log.warn("⚠️ 警告：您正在使用默认密码，建议修改配置文件中的 playground.password 以确保安全！");
            }
        } else {
            log.info("未找到playground配置，使用默认值: enabled=false, public=false");
        }
    }
}
