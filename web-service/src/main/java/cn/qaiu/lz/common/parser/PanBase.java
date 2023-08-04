package cn.qaiu.lz.common.parser;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PanBase {

    protected Promise<String> promise = Promise.promise();
    protected Logger log = LoggerFactory.getLogger(this.getClass());

    protected String key;
    protected String pwd;

    protected PanBase(String key, String pwd) {
        this.key = key;
        this.pwd = pwd;
    }

    protected void fail(Throwable t, String errorMsg, Object... args) {
        String s = String.format(errorMsg.replaceAll("\\{}", "%s"), args);
        log.error("解析异常: " + s, t.fillInStackTrace());
        promise.fail(this.getClass().getSimpleName() + ": 解析异常: " + s + " -> " + t);
    }

    protected void fail(String errorMsg, Object... args) {
        String s = String.format(errorMsg.replaceAll("\\{}", "%s"), args);
        log.error("解析异常: " + s);
        promise.fail(this.getClass().getSimpleName() + " - 解析异常: " + s);
    }

    protected Handler<Throwable> handleFail(String errorMsg) {
        return t -> fail(this.getClass().getSimpleName() + " - 请求异常 {}: -> {}", errorMsg, t.fillInStackTrace());
    }

}
