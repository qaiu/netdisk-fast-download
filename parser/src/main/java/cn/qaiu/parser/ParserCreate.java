package cn.qaiu.parser;

import cn.qaiu.entity.ShareLinkInfo;
import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

import static cn.qaiu.parser.PanDomainTemplate.KEY;
import static cn.qaiu.parser.PanDomainTemplate.PWD;


/**
 * 该类提供方法来解析和规范化不同来源的分享链接，确保它们可以转换为统一的标准链接格式。
 * 通过这种方式，应用程序可以更容易地处理和识别不同网盘服务的分享链接。
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2024/9/15 14:10
 */
public class ParserCreate {
    private final PanDomainTemplate panDomainTemplate;
    private final ShareLinkInfo shareLinkInfo;
    
    // 自定义解析器配置（与 panDomainTemplate 二选一）
    private final CustomParserConfig customParserConfig;

    private String standardUrl;
    
    // 标识是否为自定义解析器
    private final boolean isCustomParser;

    public ParserCreate(PanDomainTemplate panDomainTemplate, ShareLinkInfo shareLinkInfo) {
        this.panDomainTemplate = panDomainTemplate;
        this.shareLinkInfo = shareLinkInfo;
        this.customParserConfig = null;
        this.isCustomParser = false;
        this.standardUrl = panDomainTemplate.getStandardUrlTemplate();
    }
    
    /**
     * 自定义解析器专用构造器
     */
    private ParserCreate(CustomParserConfig customParserConfig, ShareLinkInfo shareLinkInfo) {
        this.customParserConfig = customParserConfig;
        this.shareLinkInfo = shareLinkInfo;
        this.panDomainTemplate = null;
        this.isCustomParser = true;
        this.standardUrl = customParserConfig.getStandardUrlTemplate();
    }


    // 解析并规范化分享链接
    public ParserCreate normalizeShareLink() {
        if (shareLinkInfo == null) {
            throw new IllegalArgumentException("ShareLinkInfo not init");
        }
        
        // 自定义解析器处理
        if (isCustomParser) {
            if (!customParserConfig.supportsFromShareUrl()) {
                throw new UnsupportedOperationException(
                        "自定义解析器不支持 normalizeShareLink 方法，请使用 shareKey 方法设置分享键");
            }
            
            // 使用自定义解析器的正则表达式进行匹配
            String shareUrl = shareLinkInfo.getShareUrl();
            if (StringUtils.isEmpty(shareUrl)) {
                throw new IllegalArgumentException("ShareLinkInfo shareUrl is empty");
            }
            
            java.util.regex.Matcher matcher = customParserConfig.getMatchPattern().matcher(shareUrl);
            if (matcher.matches()) {
                // 提取分享键
                try {
                    String shareKey = matcher.group("KEY");
                    if (shareKey != null) {
                        shareLinkInfo.setShareKey(shareKey);
                    }
                } catch (Exception ignored) {}
                
                // 提取密码
                try {
                    String pwd = matcher.group("PWD");
                    if (StringUtils.isNotEmpty(pwd)) {
                        shareLinkInfo.setSharePassword(pwd);
                    }
                } catch (Exception ignored) {}
                
                // 设置标准URL
                if (customParserConfig.getStandardUrlTemplate() != null) {
                    String standardUrl = customParserConfig.getStandardUrlTemplate()
                            .replace("{shareKey}", shareLinkInfo.getShareKey() != null ? shareLinkInfo.getShareKey() : "");
                    
                    // 处理密码替换
                    if (shareLinkInfo.getSharePassword() != null && !shareLinkInfo.getSharePassword().isEmpty()) {
                        standardUrl = standardUrl.replace("{pwd}", shareLinkInfo.getSharePassword());
                    } else {
                        // 如果密码为空，移除包含 {pwd} 的部分
                        standardUrl = standardUrl.replaceAll("\\?pwd=\\{pwd\\}", "").replaceAll("&pwd=\\{pwd\\}", "");
                    }
                    
                    shareLinkInfo.setStandardUrl(standardUrl);
                }
                
                return this;
            }
            throw new IllegalArgumentException("Invalid share URL for " + customParserConfig.getDisplayName());
        }
        
        // 内置解析器处理
        // 匹配并提取shareKey
        String shareUrl = shareLinkInfo.getShareUrl();
        if (StringUtils.isEmpty(shareUrl)) {
            throw new IllegalArgumentException("ShareLinkInfo shareUrl is empty");
        }
        Matcher matcher = this.panDomainTemplate.getPattern().matcher(shareUrl);
        if (matcher.find()) {
            String k0 = matcher.group(KEY);
            String shareKey = URLEncoder.encode(k0, StandardCharsets.UTF_8);

            // 返回规范化的标准链接
            standardUrl = getStandardUrlTemplate()
                    .replace("{shareKey}", k0);

            try {
                String pwd = matcher.group(PWD);
                if (StringUtils.isNotEmpty(pwd)) {
                    shareLinkInfo.setSharePassword(pwd);
                }
                standardUrl = standardUrl.replace("{pwd}", pwd);
            } catch (Exception ignored) {}

            shareLinkInfo.setShareUrl(shareUrl);
            shareLinkInfo.setShareKey(shareKey);
            if (!(panDomainTemplate.ordinal() >= PanDomainTemplate.CE.ordinal())) {
                shareLinkInfo.setStandardUrl(standardUrl);
            }
            return this;
        }
        throw new IllegalArgumentException("Invalid share URL for " + this.panDomainTemplate.getDisplayName());
    }

    public IPanTool createTool() {
        if (shareLinkInfo == null || StringUtils.isEmpty(shareLinkInfo.getType())) {
            throw new IllegalArgumentException("ShareLinkInfo not init or type is empty");
        }
        
        // 自定义解析器处理
        if (isCustomParser) {
            try {
                return this.customParserConfig.getToolClass()
                        .getDeclaredConstructor(ShareLinkInfo.class)
                        .newInstance(shareLinkInfo);
            } catch (Exception e) {
                throw new RuntimeException("无法创建自定义工具实例: " + 
                        customParserConfig.getToolClass().getName(), e);
            }
        }
        
        // 内置解析器处理
        if (StringUtils.isEmpty(shareLinkInfo.getShareKey())) {
            this.normalizeShareLink();
        }
        try {
            return this.panDomainTemplate.getToolClass()
                    .getDeclaredConstructor(ShareLinkInfo.class)
                    .newInstance(shareLinkInfo);
        } catch (Exception e) {
            throw new RuntimeException("无法创建工具实例: " + panDomainTemplate.getToolClass().getName(), e);
        }
    }

    // set share key
    public ParserCreate shareKey(String shareKey) {
        // 自定义解析器处理
        if (isCustomParser) {
            shareLinkInfo.setShareKey(shareKey);
            if (standardUrl != null) {
                standardUrl = standardUrl.replace("{shareKey}", shareKey);
                shareLinkInfo.setStandardUrl(standardUrl);
            }
            if (StringUtils.isEmpty(shareLinkInfo.getShareUrl())) {
                shareLinkInfo.setShareUrl(standardUrl != null ? standardUrl : shareKey);
            }
            return this;
        }
        
        // 内置解析器处理
        if (panDomainTemplate.ordinal() >= PanDomainTemplate.CE.ordinal()) {
            // 处理Cloudreve(ce)类: pan.huang1111.cn_s_wDz5TK _ -> /
            String[] s = shareKey.split("_");
            String standardUrl = "https://" + String.join("/", s);
            shareLinkInfo.setShareKey(s[s.length - 1]);
            shareLinkInfo.setStandardUrl(standardUrl);
            shareLinkInfo.setShareUrl(standardUrl);
        } else {
            shareLinkInfo.setShareKey(shareKey);
            standardUrl = standardUrl.replace("{shareKey}", shareKey);
            shareLinkInfo.setStandardUrl(standardUrl);
        }
        if (StringUtils.isEmpty(shareLinkInfo.getShareUrl())) {
            shareLinkInfo.setShareUrl(standardUrl);
        }
        return this;
    }

    // set any share url
    public ParserCreate fromAnyShareUrl(String url) {
        shareLinkInfo.setStandardUrl(url);
        shareLinkInfo.setShareUrl(url);
        return this;
    }

    public String getStandardUrlTemplate() {
        if (isCustomParser) {
            return this.customParserConfig.getStandardUrlTemplate();
        }
        return this.panDomainTemplate.getStandardUrlTemplate();
    }

    public ShareLinkInfo getShareLinkInfo() {
        return shareLinkInfo;
    }

    public ParserCreate setShareLinkInfoPwd(String pwd) {
        if (pwd != null) {
            shareLinkInfo.setSharePassword(pwd);
            standardUrl = standardUrl.replace("{pwd}", pwd);
            shareLinkInfo.setStandardUrl(standardUrl);
            if (shareLinkInfo.getShareUrl().contains("{pwd}")) {
                shareLinkInfo.setShareUrl(standardUrl);
            }
        }
        return this;
    }

    // 根据分享链接获取PanDomainTemplate实例（优先匹配自定义解析器）
    public synchronized static ParserCreate fromShareUrl(String shareUrl) {
        // 优先查找支持正则匹配的自定义解析器
        for (CustomParserConfig customConfig : CustomParserRegistry.getAll().values()) {
            if (customConfig.supportsFromShareUrl()) {
                java.util.regex.Matcher matcher = customConfig.getMatchPattern().matcher(shareUrl);
                if (matcher.matches()) {
                    ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                            .type(customConfig.getType())
                            .panName(customConfig.getDisplayName())
                            .shareUrl(shareUrl)
                            .build();
                    
                    // 提取分享键和密码
                    try {
                        String shareKey = matcher.group("KEY");
                        if (shareKey != null) {
                            shareLinkInfo.setShareKey(shareKey);
                        }
                    } catch (Exception ignored) {}
                    
                    try {
                        String password = matcher.group("PWD");
                        if (password != null) {
                            shareLinkInfo.setSharePassword(password);
                        }
                    } catch (Exception ignored) {}
                    
                    // 设置标准URL（如果有模板）
                    if (customConfig.getStandardUrlTemplate() != null) {
                        String standardUrl = customConfig.getStandardUrlTemplate()
                                .replace("{shareKey}", shareLinkInfo.getShareKey() != null ? shareLinkInfo.getShareKey() : "");
                        
                        // 处理密码替换
                        if (shareLinkInfo.getSharePassword() != null && !shareLinkInfo.getSharePassword().isEmpty()) {
                            standardUrl = standardUrl.replace("{pwd}", shareLinkInfo.getSharePassword());
                        } else {
                            // 如果密码为空，移除包含 {pwd} 的部分
                            standardUrl = standardUrl.replaceAll("\\?pwd=\\{pwd\\}", "").replaceAll("&pwd=\\{pwd\\}", "");
                        }
                        
                        shareLinkInfo.setStandardUrl(standardUrl);
                    }
                    
                    return new ParserCreate(customConfig, shareLinkInfo);
                }
            }
        }
        
        // 查找内置解析器
        for (PanDomainTemplate panDomainTemplate : PanDomainTemplate.values()) {
            if (panDomainTemplate.getPattern().matcher(shareUrl).matches()) {
                ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                        .type(panDomainTemplate.name().toLowerCase())
                        .panName(panDomainTemplate.getDisplayName())
                        .shareUrl(shareUrl).build();
                if (panDomainTemplate.ordinal() >= PanDomainTemplate.CE.ordinal()) {
                    shareLinkInfo.setStandardUrl(shareUrl);
                }
                ParserCreate parserCreate = new ParserCreate(panDomainTemplate, shareLinkInfo);
                return parserCreate.normalizeShareLink();
            }
        }
        throw new IllegalArgumentException("Unsupported share URL");
    }

    // 根据type获取枚举实例（优先查找自定义解析器）
    public synchronized static ParserCreate fromType(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("type不能为空");
        }
        
        String normalizedType = type.toLowerCase();
        
        // 优先查找自定义解析器
        CustomParserConfig customConfig = CustomParserRegistry.get(normalizedType);
        if (customConfig != null) {
            ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                    .type(normalizedType)
                    .panName(customConfig.getDisplayName())
                    .build();
            return new ParserCreate(customConfig, shareLinkInfo);
        }
        
        // 查找内置解析器
        try {
            PanDomainTemplate panDomainTemplate = Enum.valueOf(PanDomainTemplate.class, type.toUpperCase());
            ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                    .type(normalizedType)
                    .panName(panDomainTemplate.getDisplayName())
                    .build();
            return new ParserCreate(panDomainTemplate, shareLinkInfo);
        } catch (IllegalArgumentException ignore) {
            // 如果没有找到对应的解析器，抛出异常
            throw new IllegalArgumentException("未找到类型为 '" + type + "' 的解析器，" +
                    "请检查是否已注册自定义解析器或使用正确的内置类型");
        }
    }

    // 生成parser短链path(不包含domainName)
    public String genPathSuffix() {
        String path;
        
        // 自定义解析器处理
        if (isCustomParser) {
            path = this.shareLinkInfo.getType() + "/" + this.shareLinkInfo.getShareKey();
        } else if (panDomainTemplate.ordinal() >= PanDomainTemplate.CE.ordinal()) {
            // 处理Cloudreve(ce)类: pan.huang1111.cn_s_wDz5TK _ -> /
            path = this.shareLinkInfo.getType() + "/"
                    + this.shareLinkInfo.getShareUrl()
                    .substring("https://".length()).replace("/", "_");
        } else {
            path = this.shareLinkInfo.getType() + "/" + this.shareLinkInfo.getShareKey();
        }
        
        String sharePassword = this.shareLinkInfo.getSharePassword();
        return path + (StringUtils.isBlank(sharePassword) ? "" : ("@" + sharePassword));
    }
    
    /**
     * 判断当前是否为自定义解析器
     * @return true表示自定义解析器，false表示内置解析器
     */
    public boolean isCustomParser() {
        return isCustomParser;
    }
    
    /**
     * 获取自定义解析器配置（仅当isCustomParser为true时有效）
     * @return 自定义解析器配置，如果不是自定义解析器则返回null
     */
    public CustomParserConfig getCustomParserConfig() {
        return customParserConfig;
    }
    
    /**
     * 获取内置解析器模板（仅当isCustomParser为false时有效）
     * @return 内置解析器模板，如果是自定义解析器则返回null
     */
    public PanDomainTemplate getPanDomainTemplate() {
        return panDomainTemplate;
    }

}
