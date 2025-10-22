package cn.qaiu.parser.custom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.qaiu.parser.PanDomainTemplate;
import cn.qaiu.parser.customjs.JsScriptLoader;
import cn.qaiu.parser.customjs.JsScriptMetadataParser;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义解析器注册中心
 * 用户可以通过此类注册自己的解析器实现
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/17
 */
public class CustomParserRegistry {

    private static final Logger log = LoggerFactory.getLogger(CustomParserRegistry.class);

    /**
     * 存储自定义解析器配置的Map，key为类型标识，value为配置对象
     */
    private static final Map<String, CustomParserConfig> CUSTOM_PARSERS = new ConcurrentHashMap<>();

    /**
     * 注册自定义解析器
     *
     * @param config 解析器配置
     * @throws IllegalArgumentException 如果type已存在或与内置解析器冲突
     */
    public static void register(CustomParserConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config不能为空");
        }

        String type = config.getType().toLowerCase();

        // 检查是否与内置枚举冲突
        try {
            PanDomainTemplate.valueOf(type.toUpperCase());
            throw new IllegalArgumentException(
                    "类型标识 '" + type + "' 与内置解析器冲突，请使用其他标识"
            );
        } catch (IllegalArgumentException e) {
            // 如果valueOf抛出异常，说明不存在该枚举，这是正常情况
            if (e.getMessage().startsWith("类型标识")) {
                throw e; // 重新抛出我们自己的异常
            }
        }

        // 检查是否已注册
        if (CUSTOM_PARSERS.containsKey(type)) {
            throw new IllegalArgumentException(
                    "类型标识 '" + type + "' 已被注册，请先注销或使用其他标识"
            );
        }

        CUSTOM_PARSERS.put(type, config);
        log.info("注册自定义解析器成功: {} ({})", config.getDisplayName(), type);
    }

    /**
     * 注册JavaScript解析器
     *
     * @param config JavaScript解析器配置
     * @throws IllegalArgumentException 如果type已存在或与内置解析器冲突
     */
    public static void registerJs(CustomParserConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config不能为空");
        }
        
        if (!config.isJsParser()) {
            throw new IllegalArgumentException("config必须是JavaScript解析器配置");
        }
        
        register(config);
    }

    /**
     * 从JavaScript代码字符串注册解析器
     *
     * @param jsCode JavaScript代码
     * @throws IllegalArgumentException 如果解析失败
     */
    public static void registerJsFromCode(String jsCode) {
        if (jsCode == null || jsCode.trim().isEmpty()) {
            throw new IllegalArgumentException("JavaScript代码不能为空");
        }
        
        try {
            CustomParserConfig config = JsScriptMetadataParser.parseScript(jsCode);
            registerJs(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("解析JavaScript代码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从文件注册JavaScript解析器
     *
     * @param filePath 文件路径
     * @throws IllegalArgumentException 如果文件不存在或解析失败
     */
    public static void registerJsFromFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        
        try {
            CustomParserConfig config = JsScriptLoader.loadFromFile(filePath);
            registerJs(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("从文件加载JavaScript解析器失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从资源文件注册JavaScript解析器
     *
     * @param resourcePath 资源路径
     * @throws IllegalArgumentException 如果资源不存在或解析失败
     */
    public static void registerJsFromResource(String resourcePath) {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            throw new IllegalArgumentException("资源路径不能为空");
        }
        
        try {
            CustomParserConfig config = JsScriptLoader.loadFromResource(resourcePath);
            registerJs(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("从资源加载JavaScript解析器失败: " + e.getMessage(), e);
        }
    }

    /**
     * 自动加载所有JavaScript脚本
     */
    public static void autoLoadJsScripts() {
        try {
            List<CustomParserConfig> configs = JsScriptLoader.loadAllScripts();
            int successCount = 0;
            int failCount = 0;
            
            for (CustomParserConfig config : configs) {
                try {
                    registerJs(config);
                    successCount++;
                } catch (Exception e) {
                    log.error("加载JavaScript脚本失败: {}", config.getType(), e);
                    failCount++;
                }
            }
            
            log.info("自动加载JavaScript脚本完成: 成功 {} 个，失败 {} 个", successCount, failCount);
            
        } catch (Exception e) {
            log.error("自动加载JavaScript脚本时发生异常", e);
        }
    }

    /**
     * 注销自定义解析器
     *
     * @param type 解析器类型标识
     * @return 是否注销成功
     */
    public static boolean unregister(String type) {
        if (type == null || type.trim().isEmpty()) {
            return false;
        }
        
        CustomParserConfig removed = CUSTOM_PARSERS.remove(type.toLowerCase());
        if (removed != null) {
            log.info("注销自定义解析器: {} ({})", removed.getDisplayName(), type);
            return true;
        }
        return false;
    }

    /**
     * 根据类型获取自定义解析器配置
     *
     * @param type 解析器类型标识
     * @return 解析器配置，如果不存在则返回null
     */
    public static CustomParserConfig get(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        return CUSTOM_PARSERS.get(type.toLowerCase());
    }

    /**
     * 检查指定类型的解析器是否已注册
     *
     * @param type 解析器类型标识
     * @return 是否已注册
     */
    public static boolean contains(String type) {
        if (type == null || type.trim().isEmpty()) {
            return false;
        }
        return CUSTOM_PARSERS.containsKey(type.toLowerCase());
    }

    /**
     * 清空所有自定义解析器
     */
    public static void clear() {
        CUSTOM_PARSERS.clear();
    }

    /**
     * 获取已注册的自定义解析器数量
     *
     * @return 数量
     */
    public static int size() {
        return CUSTOM_PARSERS.size();
    }

    /**
     * 获取所有已注册的自定义解析器配置（只读视图）
     *
     * @return 不可修改的Map
     */
    public static Map<String, CustomParserConfig> getAll() {
        return Map.copyOf(CUSTOM_PARSERS);
    }
}

