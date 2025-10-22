package cn.qaiu.parser.customjs;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.qaiu.parser.custom.CustomParserConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaScript脚本元数据解析器
 * 解析类油猴格式的元数据注释
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/17
 */
public class JsScriptMetadataParser {
    
    private static final Logger log = LoggerFactory.getLogger(JsScriptMetadataParser.class);
    
    // 元数据块匹配正则
    private static final Pattern METADATA_BLOCK_PATTERN = Pattern.compile(
        "//\\s*==UserScript==\\s*(.*?)\\s*//\\s*==/UserScript==", 
        Pattern.DOTALL
    );
    
    // 元数据行匹配正则
    private static final Pattern METADATA_LINE_PATTERN = Pattern.compile(
        "//\\s*@(\\w+)\\s+(.*)"
    );
    
    /**
     * 解析JavaScript脚本，提取元数据并构建CustomParserConfig
     *
     * @param jsCode JavaScript代码
     * @return CustomParserConfig配置对象
     * @throws IllegalArgumentException 如果解析失败或缺少必填字段
     */
    public static CustomParserConfig parseScript(String jsCode) {
        if (StringUtils.isBlank(jsCode)) {
            throw new IllegalArgumentException("JavaScript代码不能为空");
        }
        
        // 1. 提取元数据块
        Map<String, String> metadata = extractMetadata(jsCode);
        
        // 2. 验证必填字段
        validateRequiredFields(metadata);
        
        // 3. 构建CustomParserConfig
        return buildConfig(metadata, jsCode);
    }
    
    /**
     * 提取元数据
     */
    private static Map<String, String> extractMetadata(String jsCode) {
        Map<String, String> metadata = new HashMap<>();
        
        Matcher blockMatcher = METADATA_BLOCK_PATTERN.matcher(jsCode);
        if (!blockMatcher.find()) {
            throw new IllegalArgumentException("未找到元数据块，请确保包含 // ==UserScript== ... // /UserScript== 格式的注释");
        }
        
        String metadataBlock = blockMatcher.group(1);
        Matcher lineMatcher = METADATA_LINE_PATTERN.matcher(metadataBlock);
        
        while (lineMatcher.find()) {
            String key = lineMatcher.group(1).toLowerCase();
            String value = lineMatcher.group(2).trim();
            metadata.put(key, value);
        }
        
        log.debug("解析到元数据: {}", metadata);
        return metadata;
    }
    
    /**
     * 验证必填字段
     */
    private static void validateRequiredFields(Map<String, String> metadata) {
        if (!metadata.containsKey("name")) {
            throw new IllegalArgumentException("缺少必填字段 @name");
        }
        if (!metadata.containsKey("type")) {
            throw new IllegalArgumentException("缺少必填字段 @type");
        }
        if (!metadata.containsKey("displayname")) {
            throw new IllegalArgumentException("缺少必填字段 @displayName");
        }
        if (!metadata.containsKey("match")) {
            throw new IllegalArgumentException("缺少必填字段 @match");
        }
        
        // 验证match字段包含KEY命名捕获组
        String matchPattern = metadata.get("match");
        if (!matchPattern.contains("(?<KEY>")) {
            throw new IllegalArgumentException("@match 正则表达式必须包含命名捕获组 KEY，用于提取分享键");
        }
    }
    
    /**
     * 构建CustomParserConfig
     */
    private static CustomParserConfig buildConfig(Map<String, String> metadata, String jsCode) {
        CustomParserConfig.Builder builder = CustomParserConfig.builder()
                .type(metadata.get("type"))
                .displayName(metadata.get("displayname"))
                .isJsParser(true)
                .jsCode(jsCode)
                .metadata(metadata);
        
        // 设置匹配正则
        String matchPattern = metadata.get("match");
        if (StringUtils.isNotBlank(matchPattern)) {
            builder.matchPattern(matchPattern);
        }
        
        // 设置可选字段
        if (metadata.containsKey("description")) {
            // description字段可以用于其他用途，暂时不存储到config中
        }
        
        if (metadata.containsKey("author")) {
            // author字段可以用于其他用途，暂时不存储到config中
        }
        
        if (metadata.containsKey("version")) {
            // version字段可以用于其他用途，暂时不存储到config中
        }
        
        return builder.build();
    }
    
    /**
     * 检查JavaScript代码是否包含有效的元数据块
     *
     * @param jsCode JavaScript代码
     * @return true表示包含有效元数据，false表示不包含
     */
    public static boolean hasValidMetadata(String jsCode) {
        if (StringUtils.isBlank(jsCode)) {
            return false;
        }
        
        try {
            Map<String, String> metadata = extractMetadata(jsCode);
            validateRequiredFields(metadata);
            return true;
        } catch (Exception e) {
            log.debug("JavaScript代码不包含有效元数据: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 从JavaScript代码中提取脚本名称
     *
     * @param jsCode JavaScript代码
     * @return 脚本名称，如果未找到则返回null
     */
    public static String extractScriptName(String jsCode) {
        if (StringUtils.isBlank(jsCode)) {
            return null;
        }
        
        try {
            Map<String, String> metadata = extractMetadata(jsCode);
            return metadata.get("name");
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 从JavaScript代码中提取脚本类型
     *
     * @param jsCode JavaScript代码
     * @return 脚本类型，如果未找到则返回null
     */
    public static String extractScriptType(String jsCode) {
        if (StringUtils.isBlank(jsCode)) {
            return null;
        }
        
        try {
            Map<String, String> metadata = extractMetadata(jsCode);
            return metadata.get("type");
        } catch (Exception e) {
            return null;
        }
    }
}
