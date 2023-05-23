package cn.qaiu.web.test;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

public class TestWebClient2 {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        WebClient client = WebClient.create(vertx);
        client.getAbs("https://qaiu.top").send().onSuccess(res -> {
            System.out.println(res.bodyAsString());
            client.close();
        });
    }
}
