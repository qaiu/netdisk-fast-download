package cn.qaiu.parser;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.impl.*;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 枚举类 PanDomainTemplate 定义了不同网盘服务的模板信息，包括：
 * <ul>
 *     <li>displayName: 网盘服务的显示名称，用于用户界面展示。</li>
 *     <li>shortName: 网盘服务的简称，用于内部逻辑处理，例如API路径。</li>
 *     <li>regexPattern: 用于匹配和解析分享链接的正则表达式。</li>
 *     <li>standardUrlTemplate: 网盘服务的标准URL模板，用于规范化分享链接。</li>
 *     <li>toolClass: 网盘解析工具实现类。</li>
 * </ul>
 * 该类提供方法来解析和规范化不同来源的分享链接，确保它们可以转换为统一的标准链接格式。
 * 通过这种方式，应用程序可以更容易地处理和识别不同网盘服务的分享链接.
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * at 2023/6/13 4:26
 */
public enum PanDomainTemplate {

    // 网盘定义
    LZ("蓝奏云",
            "lz",
            "https://([a-z]+)?\\.?lanzou[a-z]\\.com/(.+/)?(.+)",
            "https://lanzoux.com/{shareKey}",
            LzTool.class),

    // https://www.feijix.com/s/
    // https://share.feijipan.com/s/
    FJ("小飞机网盘",
            "fj",
            "https://(share\\.feijipan\\.com|www\\.feijix\\.com)/s/(.+)",
            "https://www.feijix.com/s/{shareKey}",
            FjTool.class),

    // https://lecloud.lenovo.com/share/
    LE("联想乐云",
            "le",
            "https://lecloud?\\.lenovo\\.com/share/(.+)",
            "https://lecloud.lenovo.com/share/{shareKey}",
            LeTool.class),

    // https://v2.fangcloud.com/s/
    FC("亿方云",
            "fc",
            "https://v2\\.fangcloud\\.(com|cn)/(s|sharing)/([^/]+)",
            "https://v2.fangcloud.com/s/{shareKey}",
            FcTool.class),
    // https://www.ilanzou.com/s/
    IZ("蓝奏云优享",
            "iz",
            "https://www\\.ilanzou\\.com/s/(.+)",
            "https://www.ilanzou.com/s/{shareKey}",
            IzTool.class),
    // https://wx.mail.qq.com/ftn/download?
    QQ("QQ邮箱中转站",
            "qq",
            "https://i?wx\\.mail\\.qq\\.com/ftn/download\\?(.+)",
            "https://iwx.mail.qq.com/ftn/download/{shareKey}",
            QQTool.class),
    // https://f.ws59.cn/f/或者https://www.wenshushu.cn/f/
    WS("文叔叔",
            "ws",
            "https://(f\\.ws59\\.cn|www\\.wenshushu\\.cn)/f/(.+)",
            "https://f.ws59.cn/f/{shareKey}",
            WsTool.class),
    // https://www.123pan.com/s/
    YE("123网盘",
            "ye",
            "https://www\\.123pan\\.com/s/(.+)\\.html",
            "https://www.123pan.com/s/{shareKey}.html",
            YeTool.class),
    // https://www.ecpan.cn/web/#/yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data={code}&isShare=1
    EC("移动云空间",
            "ec",
            "https://www\\.ecpan\\.cn/web(/%23|/#)?/yunpanProxy\\?path=.*&data=" +
                    "([^&]+)&isShare=1",
            "https://www.ecpan.cn/web/#/yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data={shareKey}&isShare=1",
            EcTool.class),
    // https://cowtransfer.com/s/
    COW("奶牛快传",
            "cow",
            "https://(.*)cowtransfer\\.com/s/(.+)",
            "https://cowtransfer.com/s/{shareKey}",
            CowTool.class),
    // https://pan.huang1111.cn/s/
    CE("huang1111",
               "ce",
               "https://pan\\.huang1111\\.cn/s/(.+)",
               "https://pan.huang1111.cn/s/{shareKey}",
       CeTool.class);


    // 网盘的显示名称，用于用户界面显示
    private final String displayName;

    // 网盘的简短名称，用于内部逻辑处理，如REST API路径
    private final String shortName;

    // 用于匹配和解析分享链接的正则表达式
    private final String regexPattern;

    // 网盘的标准链接模板，不含占位符，用于规范化分享链接
    private final String standardUrlTemplate;
    private final ShareLinkInfo shareLinkInfo;
    // 指向IPanTool实现类
    private final Class<? extends IPanTool> toolClass;

    PanDomainTemplate(String displayName, String shortName, String regexPattern,
                      String standardUrlTemplate, Class<? extends IPanTool> toolClass) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.regexPattern = regexPattern;
        this.standardUrlTemplate = standardUrlTemplate;
        this.toolClass = toolClass;
        this.shareLinkInfo = ShareLinkInfo.newBuilder().type(shortName).build();
    }


    // 解析并规范化分享链接
    synchronized public PanDomainTemplate normalizeShareLink() {
        if (shareLinkInfo == null) {
            throw new IllegalArgumentException("ShareLinkInfo not init");
        }
        // 匹配并提取shareKey
        String shareUrl = shareLinkInfo.getShareUrl();
        if (StringUtils.isEmpty(shareUrl)) {
            throw new IllegalArgumentException("ShareLinkInfo shareUrl is empty");
        }
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(shareUrl);
        if (matcher.find()) {
            String shareKey = matcher.group(matcher.groupCount());
            // 返回规范化的标准链接
            String standardUrl = standardUrlTemplate.replace("{shareKey}", shareKey);
            shareLinkInfo.setShareUrl(shareUrl);
            shareLinkInfo.setShareKey(shareKey);
            shareLinkInfo.setStandardUrl(standardUrl);
            return this;
        }
        throw new IllegalArgumentException("Invalid share URL for " + displayName);
    }

    public IPanTool createTool() {
        if (shareLinkInfo == null || StringUtils.isEmpty(shareLinkInfo.getType())) {
            throw new IllegalArgumentException("ShareLinkInfo not init or type is empty");
        }
        if (StringUtils.isEmpty(shareLinkInfo.getShareKey())) {
            this.normalizeShareLink();
        }
        try {
            return toolClass
                    .getDeclaredConstructor(ShareLinkInfo.class)
                    .newInstance(shareLinkInfo);
        } catch (Exception e) {
            throw new RuntimeException("无法创建工具实例: " + toolClass.getName(), e);
        }
    }

    // 生成分享链接的方法
    synchronized public PanDomainTemplate generateShareLink(String shareKey) {
        shareLinkInfo.setShareKey(shareKey);
        shareLinkInfo.setStandardUrl(standardUrlTemplate.replace("{shareKey}", shareKey));
        return this;
    }


    public String getDisplayName() {
        return this.displayName;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public String getStandardUrlTemplate() {
        return standardUrlTemplate;
    }


    public ShareLinkInfo getShareLinkInfo() {
        return shareLinkInfo;
    }

    synchronized public PanDomainTemplate setShareLinkInfoPwd(String pwd) {
        shareLinkInfo.setSharePassword(pwd);
        return this;
    }

    synchronized public PanDomainTemplate setShareLinkInfoUrl(String pwd) {
        shareLinkInfo.setSharePassword(pwd);
        return this;
    }

    // 根据分享链接获取PanDomainTemplate实例
    synchronized public static PanDomainTemplate fromShareUrl(String shareUrl) {
        for (PanDomainTemplate template : values()) {
            if (shareUrl.matches(template.regexPattern)) {
                template.getShareLinkInfo().setShareUrl(shareUrl);
                return template.normalizeShareLink();
            }
        }
        throw new IllegalArgumentException("Unsupported share URL");
    }

    // 根据shortName获取枚举实例
    public static PanDomainTemplate fromShortName(String shortName) {
        try {
            return Enum.valueOf(PanDomainTemplate.class, shortName.toUpperCase());
        } catch (IllegalArgumentException ignore) {
            // 如果没有找到对应的枚举实例，抛出异常
            throw new IllegalArgumentException("No enum constant for short name: " + shortName);
        }
    }

    public String getShortName() {
        return shortName;
    }
}
