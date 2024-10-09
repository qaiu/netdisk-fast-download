package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo; 
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class QkTool extends PanBase {

    public QkTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        final String key = shareLinkInfo.getShareKey();
        final String pwd = shareLinkInfo.getSharePassword();

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


    }
}
