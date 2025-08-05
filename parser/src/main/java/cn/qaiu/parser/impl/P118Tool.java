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
                        //c:  0x63
                        //o:  0x6F
                        //m:  0x6D
                        //1:  0x31
                        ///:  0x2F
                        char[] chars1 = new char[]{99, 111, 109, 49, 47};
                        char[] chars2 = new char[]{99, 111, 109, 47};
                        String group = matcher.group(1).replace(String.valueOf(chars1), String.valueOf(chars2));
                        System.out.println(group);
                        complete(group);
                    } else {
                        fail();
                    }
                }).onFailure(handleFail(""));
        return future();
    }
}
