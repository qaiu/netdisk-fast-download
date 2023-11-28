package cn.qaiu.parser.impl;

import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;

public class QkTool extends PanBase implements IPanTool {

    public QkTool(String key, String pwd) {
        super(key, pwd);
    }

    public Future<String> parse() {
        promise.complete("https://lz.qaiu.top");
        return promise.future();
    }
}
