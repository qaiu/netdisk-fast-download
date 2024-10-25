package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;

/**
 * 其他网盘解析
 */
public class OtherTool extends PanBase {
    private static final String API_URL_PREFIX = "";

    public OtherTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        // TODO
        fail("暂未实现, 敬请期待");
        return future();
    }
}
