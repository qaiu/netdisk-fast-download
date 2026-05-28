package cn.qaiu.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientSession;

public class ReqIpUtil {
    public static String BASE_URL = "https://ip.ihuan.me";
    public static String BASE_URL_TEMPLATE = BASE_URL + "/{path}";

    // GET https://ip.ihuan.me/mouse.do -> $("input[name='key']").val("30b4975b5547fed806bd2b9caa18485a");
    public static String PATH1 = "mouse.do";

    public static String PATH2 = "tqdl.html";

    // 创建请求头Map
    static MultiMap headers = new HeadersMultiMap();

    static {

        headers.set("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        headers.set("accept-language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.set("cache-control", "no-cache");
        headers.set("dnt", "1");
        headers.set("origin", "https://ip.ihuan.me");
        headers.set("pragma", "no-cache");
        headers.set("priority", "u=0, i");
        headers.set("referer", "https://ip.ihuan.me");
        headers.set("sec-ch-ua", "\"Google Chrome\";v=\"129\", \"Not=A?Brand\";v=\"8\", \"Chromium\";v=\"129\"");
        headers.set("sec-ch-ua-mobile", "?0");
        headers.set("sec-ch-ua-platform", "\"Windows\"");
        headers.set("sec-fetch-dest", "document");
        headers.set("sec-fetch-mode", "navigate");
        headers.set("sec-fetch-site", "same-origin");
        headers.set("sec-fetch-user", "?1");
        headers.set("upgrade-insecure-requests", "1");
//        headers.set("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36");

    }


    Vertx vertx = Vertx.vertx();
    WebClient webClient = WebClient.create(vertx);
    // 发送GET请求
    WebClientSession webClientSession = WebClientSession.create(webClient);


    public void exec() {
        webClientSession.getAbs(BASE_URL)
            .putHeaders(headers) // 将请求头Map添加到请求中
            .send(this::next);
    }

    void next(AsyncResult<HttpResponse<Buffer>> response) {
        if (response.failed()) {
            response.cause().printStackTrace();
        } else {
            HttpResponse<Buffer> res = response.result();
            System.out.println("Received response with status code " + res.statusCode());
            System.out.println("Body: " + res.body());
            webClientSession.getAbs(BASE_URL_TEMPLATE).setTemplateParam("path", PATH1)
                    .putHeaders(headers) // 将请求头Map添加到请求中
                    .send(response2 -> {
                        System.out.println(response2.result().bodyAsString());
                    });
        }

    }
}
