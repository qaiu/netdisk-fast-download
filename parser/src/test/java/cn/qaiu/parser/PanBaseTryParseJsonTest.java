package cn.qaiu.parser;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 探测场景必须用 tryParseJson：asJson 会 fail()，从而阻断 nextParser。
 */
public class PanBaseTryParseJsonTest {

    private static Vertx vertx;

    @BeforeClass
    public static void setUpClass() {
        // 不在用例结束后关闭 Vertx：共享 WebClient 绑定该实例，关闭会导致同 JVM 后续测试出现 Client is closed
        vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
    }

    private static final class ProbeTool extends PanBase {
        ProbeTool() {
            super();
            this.shareLinkInfo = ShareLinkInfo.newBuilder()
                    .type("ce")
                    .panName("Cloudreve")
                    .shareKey("k")
                    .shareUrl("https://example.com/s/k")
                    .build();
        }

        @Override
        public io.vertx.core.Future<String> parse() {
            return promise.future();
        }

        JsonObject probe(HttpResponse<?> res) {
            return tryParseJson(res);
        }

        JsonObject parseOrFail(HttpResponse<?> res) {
            return asJson(res);
        }

        boolean promiseCompleted() {
            return promise.future().isComplete();
        }

        boolean promiseFailed() {
            return promise.future().failed();
        }

        String failureMessage() {
            return promise.future().cause() == null ? null : promise.future().cause().getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<Buffer> stub(String contentEncoding, String body) {
        Buffer buffer = body == null ? null : Buffer.buffer(body);
        return (HttpResponse<Buffer>) Proxy.newProxyInstance(
                HttpResponse.class.getClassLoader(),
                new Class<?>[]{HttpResponse.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getHeader":
                            String name = args != null && args.length > 0 ? String.valueOf(args[0]) : "";
                            return "Content-Encoding".equalsIgnoreCase(name) ? contentEncoding : null;
                        case "body":
                            return buffer;
                        case "bodyAsString":
                            return body;
                        case "bodyAsJsonObject":
                            if (body == null) {
                                return null;
                            }
                            return new JsonObject(body);
                        default:
                            if (method.isDefault()) {
                                return InvocationHandler.invokeDefault(proxy, method, args);
                            }
                            Class<?> type = method.getReturnType();
                            if (type == boolean.class) {
                                return false;
                            }
                            if (type == int.class || type == long.class) {
                                return 0;
                            }
                            return null;
                    }
                });
    }

    @Test
    public void tryParseJsonReturnsNullForHtmlWithoutFailingPromise() {
        ProbeTool tool = new ProbeTool();
        JsonObject json = tool.probe(stub(null, "<html><body>not json</body></html>"));
        assertNull(json);
        assertFalse(tool.promiseCompleted());
    }

    @Test
    public void tryParseJsonParsesObject() {
        ProbeTool tool = new ProbeTool();
        JsonObject json = tool.probe(stub(null, "{\"code\":0,\"data\":\"ok\"}"));
        assertEquals(0, json.getInteger("code").intValue());
        assertFalse(tool.promiseCompleted());
    }

    @Test
    public void asJsonFailsPromiseOnHtml() {
        ProbeTool tool = new ProbeTool();
        JsonObject json = tool.parseOrFail(stub(null, "<!DOCTYPE html>"));
        assertTrue(json == null || json.isEmpty());
        assertTrue(tool.promiseFailed());
        assertTrue(tool.failureMessage(), tool.failureMessage().contains("json格式异常"));
    }
}
