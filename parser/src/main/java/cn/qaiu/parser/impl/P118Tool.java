package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 118网盘解析
 */
public class P118Tool extends PanBase {

    private static final String API_URL_PREFIX = "https://qaiu.118pan.com/ajax.php";

//    private static final String

    public P118Tool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {

        client.postAbs(API_URL_PREFIX)
                .putHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .sendBuffer(Buffer.buffer("action=load_down_addr1&file_id=" + shareLinkInfo.getShareKey()))
                .onSuccess(res -> {
                    System.out.println(res.headers());
                    Pattern compile = Pattern.compile("href=\"([^\"]+)\"");
                    Matcher matcher = compile.matcher(res.bodyAsString());
                    if (matcher.find()) {
                        System.out.println(matcher.group(1));
                        complete(matcher.group(1));
                    } else {
                        fail();
                    }
                }).onFailure(handleFail(""));
        return future();
    }

//    public static void main(String[] args) {
//        String s = new P118Tool(ShareLinkInfo.newBuilder().shareUrl("https://xiguage.118pan.com/b11848261").shareKey(
//                "11848261").build()).parseSync();
//        System.out.println(s);
//    }
}
