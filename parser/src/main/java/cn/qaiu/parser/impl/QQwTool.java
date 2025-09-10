package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.Future;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQwTool extends QQTool {

    public QQwTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    @Override
    public Future<String> parse() {
        client.getAbs(shareLinkInfo.getShareUrl()).send().onSuccess(res -> {
            String html = res.bodyAsString();
            Map<String, String> stringStringMap = extractVariables(html);
            String url = stringStringMap.get("url");
            String fn = stringStringMap.get("filename");
            String size = stringStringMap.get("filesize");
            String createBy = stringStringMap.get("nick");
            FileInfo fileInfo = new FileInfo().setFileName(fn).setSize(Long.parseLong(size)).setCreateBy(createBy);
            shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
            if (url != null) {
                String url302 = url.replace("\\x26", "&");
                promise.complete(url302);

                /*
                clientNoRedirects.getAbs(url302).send().onSuccess(res2 -> {
                    MultiMap headers = res2.headers();
                    if (headers.contains("Location")) {
                        promise.complete(headers.get("Location"));
                    } else {
                        fail("找不到重定向URL");
                    }

                }).onFailure(handleFail());
                */
            } else {
                fail("分享链接解析失败, 可能是链接失效");
            }
        }).onFailure(handleFail());

        return promise.future();
    }


    private Map<String, String> extractVariables(String jsCode) {
        Map<String, String> variables = new HashMap<>();
        // 正则表达式匹配 var 变量定义
        String regex = "\\s+var\\s+(\\w+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^;\\r\\n]*))";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(jsCode);

        while (m.find()) {
            String name = m.group(1);
            String value = m.group(2) != null ? m.group(2)
                    : m.group(3) != null ? m.group(3)
                    : m.group(4);
            variables.put(name, value);
        }

        return variables;
    }
}
