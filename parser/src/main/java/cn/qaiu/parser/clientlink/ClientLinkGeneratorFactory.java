package cn.qaiu.parser.clientlink;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.clientlink.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端下载链接生成器工厂类
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class ClientLinkGeneratorFactory {
    
    private static final Logger log = LoggerFactory.getLogger(ClientLinkGeneratorFactory.class);
    
    // 存储所有注册的生成器
    private static final Map<ClientLinkType, ClientLinkGenerator> generators = new ConcurrentHashMap<>();
    
    // 静态初始化块，注册默认的生成器
    static {
        try {
            // 注册默认生成器 - 按指定顺序注册
            register(new Aria2LinkGenerator());
            register(new MotrixLinkGenerator());
            register(new BitCometLinkGenerator());
            register(new ThunderLinkGenerator());
            register(new WgetLinkGenerator());
            register(new CurlLinkGenerator());
            register(new IdmLinkGenerator());
            register(new FdmLinkGenerator());
            register(new PowerShellLinkGenerator());
            
            log.info("客户端链接生成器工厂初始化完成，已注册 {} 个生成器", generators.size());
        } catch (Exception e) {
            log.error("初始化客户端链接生成器失败", e);
        }
    }
    
    /**
     * 生成所有类型的客户端链接
     * 
     * @param info ShareLinkInfo 对象
     * @return Map<ClientLinkType, String> 格式的客户端链接集合
     */
    public static Map<ClientLinkType, String> generateAll(ShareLinkInfo info) {
        Map<ClientLinkType, String> result = new LinkedHashMap<>();
        
        if (info == null) {
            log.warn("ShareLinkInfo 为空，无法生成客户端链接");
            return result;
        }
        
        DownloadLinkMeta meta = DownloadLinkMeta.fromShareLinkInfo(info);
        if (!meta.hasValidUrl()) {
            log.warn("下载链接元数据无效，无法生成客户端链接: {}", meta);
            return result;
        }
        
        // 按照枚举顺序遍历，保证顺序
        for (ClientLinkType type : ClientLinkType.values()) {
            ClientLinkGenerator generator = generators.get(type);
            if (generator != null) {
                try {
                    if (generator.supports(meta)) {
                        String link = generator.generate(meta);
                        if (link != null && !link.trim().isEmpty()) {
                            result.put(type, link);
                        }
                    }
                } catch (Exception e) {
                    log.warn("生成 {} 客户端链接失败: {}", type.getDisplayName(), e.getMessage());
                }
            }
        }
        
        log.debug("成功生成 {} 个客户端链接", result.size());
        return result;
    }
    
    /**
     * 生成指定类型的客户端链接
     * 
     * @param info ShareLinkInfo 对象
     * @param type 客户端类型
     * @return 生成的客户端链接字符串，失败时返回 null
     */
    public static String generate(ShareLinkInfo info, ClientLinkType type) {
        if (info == null || type == null) {
            log.warn("参数为空，无法生成客户端链接: info={}, type={}", info, type);
            return null;
        }
        
        ClientLinkGenerator generator = generators.get(type);
        if (generator == null) {
            log.warn("未找到类型为 {} 的生成器", type.getDisplayName());
            return null;
        }
        
        try {
            DownloadLinkMeta meta = DownloadLinkMeta.fromShareLinkInfo(info);
            if (!generator.supports(meta)) {
                log.warn("生成器 {} 不支持该元数据", type.getDisplayName());
                return null;
            }
            
            return generator.generate(meta);
        } catch (Exception e) {
            log.error("生成 {} 客户端链接失败", type.getDisplayName(), e);
            return null;
        }
    }
    
    /**
     * 注册自定义生成器（扩展点）
     * 
     * @param generator 客户端链接生成器
     */
    public static void register(ClientLinkGenerator generator) {
        if (generator == null) {
            log.warn("尝试注册空的生成器");
            return;
        }
        
        ClientLinkType type = generator.getType();
        if (type == null) {
            log.warn("生成器的类型为空，无法注册");
            return;
        }
        
        generators.put(type, generator);
        log.info("成功注册客户端链接生成器: {}", type.getDisplayName());
    }
    
    /**
     * 注销生成器
     * 
     * @param type 客户端类型
     * @return 被注销的生成器，如果不存在则返回 null
     */
    public static ClientLinkGenerator unregister(ClientLinkType type) {
        ClientLinkGenerator removed = generators.remove(type);
        if (removed != null) {
            log.info("成功注销客户端链接生成器: {}", type.getDisplayName());
        }
        return removed;
    }
    
    /**
     * 获取所有已注册的生成器类型
     * 
     * @return 已注册的客户端类型集合
     */
    public static Map<ClientLinkType, ClientLinkGenerator> getAllGenerators() {
        Map<ClientLinkType, ClientLinkGenerator> result = new LinkedHashMap<>();
        // 按照枚举顺序添加，保证顺序
        for (ClientLinkType type : ClientLinkType.values()) {
            ClientLinkGenerator generator = generators.get(type);
            if (generator != null) {
                result.put(type, generator);
            }
        }
        return result;
    }
    
    /**
     * 检查是否已注册指定类型的生成器
     * 
     * @param type 客户端类型
     * @return true 表示已注册，false 表示未注册
     */
    public static boolean isRegistered(ClientLinkType type) {
        return generators.containsKey(type);
    }
}
