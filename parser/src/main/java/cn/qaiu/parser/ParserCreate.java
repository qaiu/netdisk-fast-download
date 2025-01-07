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
 * @date 2024/9/15 14:10
 */
public class ParserCreate {
    private final PanDomainTemplate panDomainTemplate;
    private final ShareLinkInfo shareLinkInfo;

    private String standardUrl;

    public ParserCreate(PanDomainTemplate panDomainTemplate, ShareLinkInfo shareLinkInfo) {
        this.panDomainTemplate = panDomainTemplate;
        this.shareLinkInfo = shareLinkInfo;
        this.standardUrl = panDomainTemplate.getStandardUrlTemplate();
    }


    // 解析并规范化分享链接
    public ParserCreate normalizeShareLink() {
        if (shareLinkInfo == null) {
            throw new IllegalArgumentException("ShareLinkInfo not init");
        }
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

    // 根据分享链接获取PanDomainTemplate实例
    public synchronized static ParserCreate fromShareUrl(String shareUrl) {
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

    // 根据type获取枚举实例
    public synchronized static ParserCreate fromType(String type) {
        try {
            PanDomainTemplate panDomainTemplate = Enum.valueOf(PanDomainTemplate.class, type.toUpperCase());
            ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                    .type(type.toLowerCase()).build();
            shareLinkInfo.setPanName(panDomainTemplate.getDisplayName());
            return new ParserCreate(panDomainTemplate, shareLinkInfo);
        } catch (IllegalArgumentException ignore) {
            // 如果没有找到对应的枚举实例，抛出异常
            throw new IllegalArgumentException("No enum constant for type name: " + type);
        }
    }

    // 生成parser短链path(不包含domainName)
    public String genPathSuffix() {

        String path;
        if (panDomainTemplate.ordinal() >= PanDomainTemplate.CE.ordinal()) {
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

}
