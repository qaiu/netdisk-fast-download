package cn.qaiu.parser.impl;

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
        client.getAbs(shareLinkInfo.getStandardUrl()).send().onSuccess(res -> {
            String html = res.bodyAsString();
            String url = extractVariables(html).get("url");
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
        String regex = "var\\s+(\\w+)\\s*=\\s*([\"']?)([^\"';\\s]+)\\2\n";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(jsCode);

        while (matcher.find()) {
            String variableName = matcher.group(1); // 变量名
            String variableValue = matcher.group(3); // 变量值
            variables.put(variableName, variableValue);
        }

        return variables;
    }
}
