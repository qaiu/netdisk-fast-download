package cn.qaiu.lz.common.parser;//package cn.qaiu.lz.common.parser;

import cn.qaiu.lz.common.parser.impl.*;
import io.vertx.core.Future;

public interface IPanTool {
    Future<String> parse(String data, String code);

    static IPanTool typeMatching(String type) {
        return switch (type) {
            case "lz" -> new LzTool();
            case "cow" -> new CowTool();
            case "ec" -> new EcTool();
            case "fc" -> new FcTool();
            case "uc" -> new UcTool();
            case "ye" -> new YeTool();
            case "fj" -> new FjTool();
            default -> {
                throw new IllegalArgumentException("未知分享类型");
            }
        };
    }

    static IPanTool shareURLPrefixMatching(String url) {

        if (url.startsWith(CowTool.SHARE_URL_PREFIX)) {
            return new CowTool();
        } else if (url.startsWith(EcTool.SHARE_URL_PREFIX)) {
            return new EcTool();
        } else if (url.startsWith(FcTool.SHARE_URL_PREFIX)) {
            return new FcTool();
        } else if (url.startsWith(UcTool.SHARE_URL_PREFIX)) {
            return new UcTool();
        } else if (url.startsWith(YeTool.SHARE_URL_PREFIX)) {
            return new YeTool();
        } else if (url.startsWith(FjTool.SHARE_URL_PREFIX)) {
            return new FjTool();
        } else if (url.contains("lanzou")) {
            return new LzTool();
        }

        throw new IllegalArgumentException("未知分享类型");
    }

}
