package cn.qaiu.parser.custompy;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.qaiu.parser.custom.CustomParserConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python脚本元数据解析器
 * 解析类油猴格式的元数据注释（Python风格）
 *
 * @author QAIU
 */
public class PyScriptMetadataParser {
    
    private static final Logger log = LoggerFactory.getLogger(PyScriptMetadataParser.class);
    
    // 元数据块匹配正则（Python注释风格）
    // 支持 # ==UserScript== 格式
    private static final Pattern METADATA_BLOCK_PATTERN = Pattern.compile(
        "#\\s*==UserScript==\\s*(.*?)\\s*#\\s*==/UserScript==", 
        Pattern.DOTALL
    );
    
    // 元数据行匹配正则
    private static final Pattern METADATA_LINE_PATTERN = Pattern.compile(
        "#\\s*@(\\w+)\\s+(.*)"
    );
    
    /**
     * 解析Python脚本，提取元数据并构建CustomParserConfig
     *
     * @param pyCode Python代码
     * @return CustomParserConfig配置对象
     * @throws IllegalArgumentException 如果解析失败或缺少必填字段
     */
    public static CustomParserConfig parseScript(String pyCode) {
        if (StringUtils.isBlank(pyCode)) {
            throw new IllegalArgumentException("Python代码不能为空");
        }
        
        // 1. 提取元数据块
        Map<String, String> metadata = extractMetadata(pyCode);
        
        // 2. 验证必填字段
        validateRequiredFields(metadata);
        
        // 3. 构建CustomParserConfig
        return buildConfig(metadata, pyCode);
    }
    
    /**
     * 提取元数据
     */
    private static Map<String, String> extractMetadata(String pyCode) {
        Map<String, String> metadata = new HashMap<>();
        
        Matcher blockMatcher = METADATA_BLOCK_PATTERN.matcher(pyCode);
        if (!blockMatcher.find()) {
            throw new IllegalArgumentException("未找到元数据块，请确保包含 # ==UserScript== ... # ==/UserScript== 格式的注释");
        }
        
        String metadataBlock = blockMatcher.group(1);
        Matcher lineMatcher = METADATA_LINE_PATTERN.matcher(metadataBlock);
        
        while (lineMatcher.find()) {
            String key = lineMatcher.group(1).toLowerCase();
            String value = lineMatcher.group(2).trim();
            metadata.put(key, value);
        }
        
        log.debug("解析到Python脚本元数据: {}", metadata);
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
        if (!matchPattern.contains("(?P<KEY>") && !matchPattern.contains("(?<KEY>")) {
            throw new IllegalArgumentException("@match 正则表达式必须包含命名捕获组 KEY（Python格式: (?P<KEY>...) 或 Java格式: (?<KEY>...)）");
        }
    }
    
    /**
     * 构建CustomParserConfig
     */
    private static CustomParserConfig buildConfig(Map<String, String> metadata, String pyCode) {
        CustomParserConfig.Builder builder = CustomParserConfig.builder()
                .type(metadata.get("type"))
                .displayName(metadata.get("displayname"))
                .isPyParser(true)
                .pyCode(pyCode)
                .language("python")
                .metadata(metadata);
        
        // 设置匹配正则（将Python风格的(?P<KEY>...)转换为Java风格的(?<KEY>...)）
        String matchPattern = metadata.get("match");
        if (StringUtils.isNotBlank(matchPattern)) {
            // 将Python命名捕获组转换为Java格式
            matchPattern = matchPattern.replace("(?P<", "(?<");
            builder.matchPattern(matchPattern);
        }
        
        return builder.build();
    }
    
    /**
     * 检查Python代码是否包含有效的元数据块
     *
     * @param pyCode Python代码
     * @return true表示包含有效元数据，false表示不包含
     */
    public static boolean hasValidMetadata(String pyCode) {
        if (StringUtils.isBlank(pyCode)) {
            return false;
        }
        
        try {
            Map<String, String> metadata = extractMetadata(pyCode);
            return metadata.containsKey("name") && 
                   metadata.containsKey("type") && 
                   metadata.containsKey("displayname") && 
                   metadata.containsKey("match");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取脚本类型（不验证必填字段）
     *
     * @param pyCode Python代码
     * @return 脚本类型，如果无法提取则返回null
     */
    public static String getScriptType(String pyCode) {
        if (StringUtils.isBlank(pyCode)) {
            return null;
        }
        
        try {
            Map<String, String> metadata = extractMetadata(pyCode);
            return metadata.get("type");
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取脚本显示名称（不验证必填字段）
     *
     * @param pyCode Python代码
     * @return 显示名称，如果无法提取则返回null
     */
    public static String getScriptDisplayName(String pyCode) {
        if (StringUtils.isBlank(pyCode)) {
            return null;
        }
        
        try {
            Map<String, String> metadata = extractMetadata(pyCode);
            return metadata.get("displayname");
        } catch (Exception e) {
            return null;
        }
    }
}
