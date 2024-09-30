package cn.qaiu.parser;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.WebClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 解析器抽象类包含promise, HTTP Client, 默认失败方法等;
 * 新增网盘解析器需要继承该类.
 */
public abstract class PanBase {
    protected Logger log = LoggerFactory.getLogger(this.getClass());

    protected Promise<String> promise = Promise.promise();

    /**
     * Http client
     */
    protected WebClient client = WebClient.create(WebClientVertxInit.get());

    /**
     * Http client session (会话管理, 带cookie请求)
     */
    protected WebClientSession clientSession = WebClientSession.create(client);

    /**
     * Http client 不自动跳转
     */
    protected WebClient clientNoRedirects = WebClient.create(WebClientVertxInit.get(),
            new WebClientOptions().setFollowRedirects(false));

    // test proxy
    protected WebClient proxyClient = WebClient.create(WebClientVertxInit.get(), new WebClientOptions()
            .setUserAgentEnabled(false).setFollowRedirects(false)
            .setProxyOptions(new ProxyOptions().setHost("101.251.204.174").setPort(8080).setType(ProxyType.HTTP)));

    protected ShareLinkInfo shareLinkInfo;

    /**
     * 子类重写此构造方法不需要添加额外逻辑
     * 如:
     * <blockquote><pre>
     *  public XxTool(String key, String pwd) {
     *      super(key, pwd);
     *  }
     * </pre></blockquote>
     *
     */
    public PanBase(ShareLinkInfo shareLinkInfo) {
        this.shareLinkInfo = shareLinkInfo;
    }

    /**
     * 失败时生成异常消息
     *
     * @param t        异常实例
     * @param errorMsg 提示消息
     * @param args     log参数变量
     */
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

    /**
     * 失败时生成异常消息
     *
     * @param errorMsg 提示消息
     * @param args     log参数变量
     */
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

    /**
     * 生成失败Future的处理器
     *
     * @param errorMsg 提示消息
     * @return Handler
     */
    protected Handler<Throwable> handleFail(String errorMsg) {
        return t -> fail(this.getClass().getSimpleName() + " - 请求异常 {}: -> {}", errorMsg, t.fillInStackTrace());
    }


    /**
     * bodyAsJsonObject的封装, 会自动处理异常
     * @param res HttpResponse
     * @return JsonObject
     */
    protected JsonObject asJson(HttpResponse<?> res) {
        try {
            return res.bodyAsJsonObject();
        } catch (DecodeException e) {
            fail("解析失败: json格式异常: {}", res.bodyAsString());
            throw new RuntimeException("解析失败: json格式异常");
        }
    }

}
