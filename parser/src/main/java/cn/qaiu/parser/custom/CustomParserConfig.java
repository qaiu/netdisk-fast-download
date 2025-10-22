package cn.qaiu.parser.custom;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.IPanTool;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 用户自定义解析器配置类
 * 用于描述自定义解析器的元信息
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/17
 */
public class CustomParserConfig {

    /**
     * 解析器类型标识（唯一，建议使用小写英文）
     */
    private final String type;

    /**
     * 网盘显示名称
     */
    private final String displayName;

    /**
     * 解析工具实现类（必须实现 IPanTool 接口，且有 ShareLinkInfo 单参构造器）
     */
    private final Class<? extends IPanTool> toolClass;

    /**
     * 标准URL模板（可选，用于规范化分享链接）
     */
    private final String standardUrlTemplate;

    /**
     * 网盘域名（可选）
     */
    private final String panDomain;

    /**
     * 匹配正则表达式（可选，用于从分享链接中识别和提取信息）
     * 如果提供，则支持通过 fromShareUrl 方法自动识别自定义解析器
     * 正则表达式必须包含命名捕获组 KEY，用于提取分享键
     * 可选包含命名捕获组 PWD，用于提取分享密码
     */
    private final Pattern matchPattern;

    /**
     * JavaScript代码（用于JavaScript解析器）
     */
    private final String jsCode;

    /**
     * 是否为JavaScript解析器
     */
    private final boolean isJsParser;

    /**
     * 元数据信息（从脚本注释中解析）
     */
    private final Map<String, String> metadata;

    private CustomParserConfig(Builder builder) {
        this.type = builder.type;
        this.displayName = builder.displayName;
        this.toolClass = builder.toolClass;
        this.standardUrlTemplate = builder.standardUrlTemplate;
        this.panDomain = builder.panDomain;
        this.matchPattern = builder.matchPattern;
        this.jsCode = builder.jsCode;
        this.isJsParser = builder.isJsParser;
        this.metadata = builder.metadata;
    }

    public String getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Class<? extends IPanTool> getToolClass() {
        return toolClass;
    }

    public String getStandardUrlTemplate() {
        return standardUrlTemplate;
    }

    public String getPanDomain() {
        return panDomain;
    }

    public Pattern getMatchPattern() {
        return matchPattern;
    }

    public String getJsCode() {
        return jsCode;
    }

    public boolean isJsParser() {
        return isJsParser;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 检查是否支持从分享链接自动识别
     * @return true表示支持，false表示不支持
     */
    public boolean supportsFromShareUrl() {
        return matchPattern != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 建造者类
     */
    public static class Builder {
        private String type;
        private String displayName;
        private Class<? extends IPanTool> toolClass;
        private String standardUrlTemplate;
        private String panDomain;
        private Pattern matchPattern;
        private String jsCode;
        private boolean isJsParser;
        private Map<String, String> metadata;

        /**
         * 设置解析器类型标识（必填，唯一）
         * @param type 类型标识（建议使用小写英文）
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * 设置网盘显示名称（必填）
         * @param displayName 显示名称
         */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /**
         * 设置解析工具实现类（必填）
         * @param toolClass 工具类（必须实现 IPanTool 接口）
         */
        public Builder toolClass(Class<? extends IPanTool> toolClass) {
            this.toolClass = toolClass;
            return this;
        }

        /**
         * 设置标准URL模板（可选）
         * @param standardUrlTemplate URL模板
         */
        public Builder standardUrlTemplate(String standardUrlTemplate) {
            this.standardUrlTemplate = standardUrlTemplate;
            return this;
        }

        /**
         * 设置网盘域名（可选）
         * @param panDomain 网盘域名
         */
        public Builder panDomain(String panDomain) {
            this.panDomain = panDomain;
            return this;
        }

        /**
         * 设置匹配正则表达式（可选）
         * @param pattern 正则表达式Pattern对象
         */
        public Builder matchPattern(Pattern pattern) {
            this.matchPattern = pattern;
            return this;
        }

        /**
         * 设置匹配正则表达式（可选）
         * @param regex 正则表达式字符串
         */
        public Builder matchPattern(String regex) {
            if (regex != null && !regex.trim().isEmpty()) {
                this.matchPattern = Pattern.compile(regex);
            }
            return this;
        }

        /**
         * 设置JavaScript代码（用于JavaScript解析器）
         * @param jsCode JavaScript代码
         */
        public Builder jsCode(String jsCode) {
            this.jsCode = jsCode;
            return this;
        }

        /**
         * 设置是否为JavaScript解析器
         * @param isJsParser 是否为JavaScript解析器
         */
        public Builder isJsParser(boolean isJsParser) {
            this.isJsParser = isJsParser;
            return this;
        }

        /**
         * 设置元数据信息
         * @param metadata 元数据信息
         */
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * 构建配置对象
         * @return CustomParserConfig
         */
        public CustomParserConfig build() {
            if (type == null || type.trim().isEmpty()) {
                throw new IllegalArgumentException("type不能为空");
            }
            if (displayName == null || displayName.trim().isEmpty()) {
                throw new IllegalArgumentException("displayName不能为空");
            }
            
            // 如果是JavaScript解析器，验证jsCode
            if (isJsParser) {
                if (jsCode == null || jsCode.trim().isEmpty()) {
                    throw new IllegalArgumentException("JavaScript解析器的jsCode不能为空");
                }
            } else {
                // 如果是Java解析器，验证toolClass
                if (toolClass == null) {
                    throw new IllegalArgumentException("Java解析器的toolClass不能为空");
                }
                
                // 验证toolClass是否实现了IPanTool接口
                if (!IPanTool.class.isAssignableFrom(toolClass)) {
                    throw new IllegalArgumentException("toolClass必须实现IPanTool接口");
                }
                
                // 验证toolClass是否有ShareLinkInfo单参构造器
                try {
                    toolClass.getDeclaredConstructor(ShareLinkInfo.class);
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException("toolClass必须有ShareLinkInfo单参构造器", e);
                }
            }
            
            // 验证正则表达式（如果提供）
            if (matchPattern != null) {
                // 检查正则表达式是否包含KEY命名捕获组
                String patternStr = matchPattern.pattern();
                if (!patternStr.contains("(?<KEY>")) {
                    throw new IllegalArgumentException("正则表达式必须包含命名捕获组 KEY，用于提取分享键");
                }
            }
            
            return new CustomParserConfig(this);
        }
    }

    @Override
    public String toString() {
        return "CustomParserConfig{" +
                "type='" + type + '\'' +
                ", displayName='" + displayName + '\'' +
                ", toolClass=" + (toolClass != null ? toolClass.getName() : "null") +
                ", standardUrlTemplate='" + standardUrlTemplate + '\'' +
                ", panDomain='" + panDomain + '\'' +
                ", matchPattern=" + (matchPattern != null ? matchPattern.pattern() : "null") +
                ", jsCode=" + (jsCode != null ? "[JavaScript代码]" : "null") +
                ", isJsParser=" + isJsParser +
                ", metadata=" + metadata +
                '}';
    }
}

