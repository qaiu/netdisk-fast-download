package cn.qaiu.web.test;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import io.vertx.ext.web.multipart.impl.MultipartFormImpl;

public class WebClientExample {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        WebClient client = WebClient.create(vertx);

        MultipartForm form = new MultipartFormImpl()
                .attribute("email", "736226400@qq.com")
                .attribute("password", "");

        client.postAbs("https://cowtransfer.com/api/user/emaillogin")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "multipart/form-data; boundary=WebAppBoundary")
                .sendMultipartForm(form, ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> response = ar.result();
                        System.out.println("Response status code: " + response.statusCode());

                        // Print all response headers
                        MultiMap headers = response.headers();
                        headers.names().forEach(name -> {
                            System.out.println(name + ": " + headers.getAll(name));
                        });

                        JsonObject responseBody = response.bodyAsJsonObject();
                        System.out.println("Response body: " + responseBody.encodePrettily());
                    } else {
                        System.out.println("Something went wrong: " + ar.cause().getMessage());
                    }
                    vertx.close();
                });
    }
}
