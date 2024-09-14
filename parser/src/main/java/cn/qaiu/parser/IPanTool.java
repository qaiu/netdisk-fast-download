package cn.qaiu.parser;//package cn.qaiu.lz.common.parser;

import io.vertx.core.Future;

public interface IPanTool {
    Future<String> parse();
    // 基于枚举PanDomainTemplate匹配分享链接类型
    static IPanTool typeMatching(String type, String key, String pwd) {
        try {
            return PanDomainTemplate
                    .fromShortName(type)
                    .generateShareLink(key)
                    .setShareLinkInfoPwd(pwd)
                    .createTool();
        } catch (Exception e) {
            throw new UnsupportedOperationException("未知分享类型", e);
        }
    }

    static IPanTool shareURLPrefixMatching(String url, String pwd) {
        try {
            return PanDomainTemplate
                    .fromShareUrl(url)
                    .setShareLinkInfoPwd(pwd)
                    .createTool();
        } catch (Exception e) {
            throw new UnsupportedOperationException("未知分享类型", e);
        }
    }

}
