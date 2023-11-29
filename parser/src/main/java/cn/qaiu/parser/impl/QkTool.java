package cn.qaiu.parser.impl;

import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class QkTool extends PanBase implements IPanTool {

    public QkTool(String key, String pwd) {
        super(key, pwd);
    }

    public Future<String> parse() {
        promise.complete("https://lz.qaiu.top");
        IntStream.range(0, 1000).forEach(num -> {
            clientNoRedirects.getAbs(key).send()
                    .onSuccess(res -> {
                        String location = res.headers().get("Location");
                        System.out.println(num + ":" + location);
                    })
                    .onFailure(handleFail("连接失败"));
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        return promise.future();
    }

    public static void main(String[] args) {

        new QkTool("https://pimapi.lenovomm.com/clouddiskapi/v1/shareRedirect?si=12298704&dk" +
                "=19ab590770399d4438ea885446e27186cc668cdfa559f5fcc063a1ecf78008e5&pk" +
                "=ef45aa4d25c1dcecb631b3394f51539d59cb35c6a40c3911df8ba431ba2a3244&pc=true&ot=ali&ob=sync-cloud-disk" +
                "&ok=649593714557087744.dex&fn=classes" +
                ".dex&ds=8909208&dc=1&bi=asdddsad&ri=&ts=1701235051759&sn" +
                "=13dc33749bd9cc108009fa505b3ecca9f358d70874352858475956ba4240e4c3", "")
                .parse().onSuccess((res) -> {
                });

    }
}
