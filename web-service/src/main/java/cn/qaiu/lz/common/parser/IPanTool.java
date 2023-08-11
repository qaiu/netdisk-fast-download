package cn.qaiu.lz.common.parser;//package cn.qaiu.lz.common.parser;

import cn.qaiu.lz.common.parser.impl.*;
import io.vertx.core.Future;

public interface IPanTool {
    Future<String> parse();

    static IPanTool typeMatching(String type, String key, String pwd) {
        return switch (type) {
            case "lz" -> new LzTool(key, pwd);
            case "cow" -> new CowTool(key, pwd);
            case "ec" -> new EcTool(key, pwd);
            case "fc" -> new FcTool(key, pwd);
            case "uc" -> new UcTool(key, pwd);
            case "ye" -> new YeTool(key, pwd);
            case "fj" -> new FjTool(key, pwd);
            default -> {
                throw new UnsupportedOperationException("未知分享类型");
            }
        };
    }

    static IPanTool shareURLPrefixMatching(String url, String pwd) {

        if (url.contains(CowTool.LINK_KEY)) {
            return new CowTool(url, pwd);
        } else if (url.startsWith(EcTool.SHARE_URL_PREFIX)) {
            return new EcTool(url, pwd);
        } else if (url.startsWith(FcTool.SHARE_URL_PREFIX0)) {
            return new FcTool(url, pwd);
        } else if (url.startsWith(UcTool.SHARE_URL_PREFIX)) {
            return new UcTool(url, pwd);
        } else if (url.startsWith(YeTool.SHARE_URL_PREFIX)) {
            return new YeTool(url, pwd);
        } else if (url.startsWith(FjTool.SHARE_URL_PREFIX)) {
            return new FjTool(url, pwd);
        } else if (url.contains(LzTool.LINK_KEY)) {
            return new LzTool(url, pwd);
        }

        throw new UnsupportedOperationException("未知分享类型");
    }

}
