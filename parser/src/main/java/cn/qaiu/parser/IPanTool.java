package cn.qaiu.parser;//package cn.qaiu.lz.common.parser;

import cn.qaiu.parser.impl.*;
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
            case "qk" -> new QkTool(key, pwd);
            case "le" -> new LeTool(key, pwd);
            case "ws" -> new WsTool(key, pwd);
            case "qq" -> new QQTool(key, pwd);
            case "iz" -> new IzTool(key, pwd);
            case "ce" -> new CeTool(key, pwd);
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
        } else if (url.startsWith(FjTool.SHARE_URL_PREFIX) || url.startsWith(FjTool.SHARE_URL_PREFIX2)) {
            return new FjTool(url, pwd);
        } else if (url.startsWith(IzTool.SHARE_URL_PREFIX)) {
            return new IzTool(url, pwd);
        } else if (url.contains(LzTool.LINK_KEY)) {
            return new LzTool(url, pwd);
        } else if (url.startsWith(LeTool.SHARE_URL_PREFIX)) {
            return new LeTool(url, pwd);
        } else if (url.contains(WsTool.SHARE_URL_PREFIX) || url.contains(WsTool.SHARE_URL_PREFIX2)) {
            return new WsTool(url, pwd);
        } else if (url.contains(QQTool.SHARE_URL_PREFIX)) {
            return new QQTool(url, pwd);
        } else if (url.contains("/s/")) {
            // Cloudreve 网盘通用解析
            return new CeTool(url, pwd);
        }

        throw new UnsupportedOperationException("未知分享类型");
    }

}
