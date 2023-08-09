package cn.qaiu.lz.common.parser;

import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PanBase {
    protected Logger log = LoggerFactory.getLogger(this.getClass());

    protected Promise<String> promise = Promise.promise();

    protected WebClient client = WebClient.create(VertxHolder.getVertxInstance());
    protected WebClient clientNoRedirects = WebClient.create(VertxHolder.getVertxInstance(), OPTIONS);
    private static final WebClientOptions OPTIONS = new WebClientOptions().setFollowRedirects(false);


    protected String key;
    protected String pwd;

    protected PanBase(String key, String pwd) {
        this.key = key;
        this.pwd = pwd;
    }

    protected void fail(Throwable t, String errorMsg, Object... args) {
        try {
            String s = String.format(errorMsg.replaceAll("\\{}", "%s"), args);
            log.error("解析异常: " + s, t.fillInStackTrace());
            promise.fail(this.getClass().getSimpleName() + ": 解析异常: " + s + " -> " + t);
        } catch (Exception e) {
            log.error("ErrorMsg format fail. The parameter has been discarded", e);
            log.error("解析异常: " + errorMsg, t.fillInStackTrace());
            promise.fail(this.getClass().getSimpleName() + ": 解析异常: " + errorMsg + " -> " + t);
        }
    }

    protected void fail(String errorMsg, Object... args) {
        try {
            String s = String.format(errorMsg.replaceAll("\\{}", "%s"), args);
            log.error("解析异常: " + s);
            promise.fail(this.getClass().getSimpleName() + " - 解析异常: " + s);
        } catch (Exception e) {
            log.error("ErrorMsg format fail. The parameter has been discarded", e);
            log.error("解析异常: " + errorMsg);
            promise.fail(this.getClass().getSimpleName() + " - 解析异常: " + errorMsg);
        }
    }

    protected Handler<Throwable> handleFail(String errorMsg) {
        return t -> fail(this.getClass().getSimpleName() + " - 请求异常 {}: -> {}", errorMsg, t.fillInStackTrace());
    }

}
