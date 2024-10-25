package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

/**
 * <a href="https://kodcloud.com/">可道云</a>
 */
public class KdTool extends PanBase {
    private static final String API_URL_PREFIX = "";

    public KdTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        nextParser();
        // TODO
        return future();
    }
}
