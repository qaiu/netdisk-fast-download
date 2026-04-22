package cn.qaiu.vx.core.test;

import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 集成测试: 验证 RouterHandlerFactory 对 JsonObject/JsonArray 参数绑定逻辑是否正确
 *
 * 运行方式: mvn test-compile -pl core && java -cp "core/target/test-classes:core/target/classes:..." \
 *            cn.qaiu.vx.core.test.RouterHandlerBindingTest
 *
 * 或直接在 IDE 中运行 main 方法。
 */
public class RouterHandlerBindingTest {

    static final int TEST_PORT = 18989;

    public static void main(String[] args) throws Exception {
        System.out.println("=== RouterHandler JsonObject/JsonArray 绑定测试 ===\n");

        // 1. 先初始化 Vert.x 与 VertxHolder ——必须在加载 RouterHandlerFactory 之前
        Vertx vertx = Vertx.vertx();
        VertxHolder.init(vertx);

        // 2. 向 SharedData 注入最小化配置
        //    baseLocations 指向测试包，使 Reflections 只扫描 TestJsonHandler
        vertx.sharedData().getLocalMap("local").put("customConfig", new JsonObject()
                .put("baseLocations", "cn.qaiu.vx.core.test")
                .put("routeTimeOut", 30000)
                .put("entityPackagesReg", new JsonArray()));
        // ReverseProxyVerticle.<clinit> 需要 globalConfig.proxyConf（非空字符串即可）
        vertx.sharedData().getLocalMap("local").put("globalConfig", new JsonObject()
                .put("proxyConf", "proxy.yml"));

        // 3. 创建 Router（此时才触发 BaseHttpApi.reflections 静态字段初始化）
        //    用反射延迟加载，确保上面的 SharedData 已就绪
        cn.qaiu.vx.core.handlerfactory.RouterHandlerFactory factory =
                new cn.qaiu.vx.core.handlerfactory.RouterHandlerFactory("api");
        io.vertx.ext.web.Router router = factory.createRouter();

        // 4. 启动 HTTP 服务器
        CountDownLatch latch = new CountDownLatch(1);
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(TEST_PORT, res -> {
                    if (res.succeeded()) {
                        System.out.println("✔ 测试服务器启动成功  port=" + TEST_PORT);
                    } else {
                        System.err.println("✘ 服务器启动失败: " + res.cause().getMessage());
                    }
                    latch.countDown();
                });

        if (!latch.await(5, TimeUnit.SECONDS)) {
            System.err.println("服务器启动超时");
            vertx.close();
            System.exit(1);
        }
        Thread.sleep(100); // 等 Vert.x 就绪

        // 5. 执行测试
        boolean allPassed = true;
        allPassed &= testJsonObject();
        allPassed &= testJsonArray();

        // 6. 关闭
        CountDownLatch closeLatch = new CountDownLatch(1);
        vertx.close(v -> closeLatch.countDown());
        closeLatch.await(3, TimeUnit.SECONDS);

        System.out.println("\n" + (allPassed ? "✅ 全部测试通过!" : "❌ 存在测试失败!"));
        System.exit(allPassed ? 0 : 1);
    }

    // ---------- 子测试 ----------

    private static boolean testJsonObject() throws Exception {
        String bodyStr = "{\"name\":\"test\",\"value\":123}";
        String respBody = post("/api/test/json-object", bodyStr);
        System.out.println("[JsonObject] 响应: " + respBody);

        JsonObject result = new JsonObject(respBody);
        JsonObject data = result.getJsonObject("data");
        boolean bound = data != null && Boolean.TRUE.equals(data.getBoolean("bound"));
        System.out.println("[JsonObject] " + (bound
                ? "PASS ✅  body 正确绑定为 JsonObject"
                : "FAIL ❌  body 未绑定 (null)"));
        return bound;
    }

    private static boolean testJsonArray() throws Exception {
        String bodyStr = "[1,2,3]";
        String respBody = post("/api/test/json-array", bodyStr);
        System.out.println("[JsonArray]  响应: " + respBody);

        JsonObject result = new JsonObject(respBody);
        JsonObject data = result.getJsonObject("data");
        boolean bound = data != null
                && Boolean.TRUE.equals(data.getBoolean("bound"))
                && Integer.valueOf(3).equals(data.getInteger("size"));
        System.out.println("[JsonArray]  " + (bound
                ? "PASS ✅  body 正确绑定为 JsonArray, size=3"
                : "FAIL ❌  body 未绑定 或 size 不对"));
        return bound;
    }

    private static String post(String path, String body) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }
}
