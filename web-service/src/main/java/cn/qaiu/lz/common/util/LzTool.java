package cn.qaiu.lz.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 蓝奏云解析工具
 *
 * @author QAIU
 * @version 1.0 update 2021/5/16 10:39
 */
public class LzTool {

    public static String parse(String fullUrl) throws Exception {
        var userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0" +
                ".3626.121 Safari/537.3";
        var url = fullUrl.substring(0, fullUrl.lastIndexOf('/') + 1);
        var id = fullUrl.substring(fullUrl.lastIndexOf('/') + 1);
        Map<String, String> header = new HashMap<>();
        header.put("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
        header.put("referer", url);

        /*
            // 部分链接需要设置安卓UA
            sec-ch-ua: "Google Chrome";v="111", "Not(A:Brand";v="8", "Chromium";v="111"
            sec-ch-ua-mobile: ?1
            sec-ch-ua-platform: "Android"
         */
        var userAgent2 = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like " +
                "Gecko) Chrome/111.0.0.0 Mobile Safari/537.36";
        Map<String, String> header2 = new HashMap<>();
        header2.put("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
        header2.put("sec-ch-ua-mobile", "sec-ch-ua-mobile");
        header2.put("sec-ch-ua-platform", "Android");
        header2.put("referer", url);

        //第一次请求，获取iframe的地址
        String result = Jsoup.connect(url + id)
                .userAgent(userAgent)
                .get()
                .select(".ifr2")
                .attr("src");

        //第二次请求得到js里的json数据里的sign
        result = Jsoup.connect(url + result)
                .headers(header)
                .userAgent(userAgent)
                .get()
                .html();
//        System.out.println(result);
        // 'sign':'AWcGOFprUGFWX1BvBTVXawdrBDZTOAU_bV2FTZFU7W2sBJ1t4DW0FYFIyBmgDZVJgUjAFNV41UGQFNg_c_c' 改下正则TMD
        // 最近上传竟然没_c_c
        Matcher matcher = Pattern.compile("'sign'\s*:\s*'([0-9a-zA-Z_]+)'").matcher(result);
        Map<String, String> params = new LinkedHashMap<>();
        if (matcher.find()) {
            String sn = matcher.group(1).replace("'", "");
            params.put("action", "downprocess");
            params.put("sign", sn);
            params.put("ves", "1");
//            System.out.println(sn);

        } else {
            throw new IOException();
        }
        //第三次请求 通过参数发起post请求,返回json数据
        result = Jsoup
                .connect(url + "ajaxm.php")
                .headers(header)
                .userAgent(userAgent)
                .data(params)
                .post()
                .text()
                .replace("\\", "");
        //json转为map
        params = new ObjectMapper().readValue(result, new TypeReference<>() {
        });
//        System.out.println(params);
        //通过json的数据拼接出最终的URL发起第最终请求,并得到响应信息头
        url = params.get("dom") + "/file/" + params.get("url");
        var headers = Jsoup.connect(url)
                .ignoreContentType(true)
                .userAgent(userAgent2)
                .headers(header2)
                .followRedirects(false)
                .execute()
                .headers();
        //得到重定向的地址进行重定向
        url = headers.get("Location");

        return url;
    }

    public static void main(String[] args) {
//        String key = "https://lanzoux.com/ia2cntg";
        String key = "https://wwsd.lanzoue.com/icBp6qqj82b";
        String urlPrefix = "https://lanzoux.com";
        String code = "QAIU";

        WebClient client = WebClient.create(Vertx.vertx());
        client.getAbs(key).send().onSuccess(res -> {
            String html = res.bodyAsString();
            // 匹配iframe
            Pattern compile = Pattern.compile("class=\"ifr2\" name=.+src=\"(/fn\\?[a-zA-Z0-9_+/=]{16,})\"");
            Matcher matcher = compile.matcher(html);
            if (!matcher.find()) {
                // $.ajax({
                //			type : 'post',
                //			url : '/ajaxm.php',
                //			//data : 'action=downprocess&sign=VTMAPgs6UG&p='+pwd,
                //			data : 'action=downprocess&sign
                //			=VzEBPwAxBzYIAQo1BjYBPVA_bDj1fNgEwUWNTYFUwAzMJL1V2Xj5XMlMzVjhTMVFjUzEHOlM4AjEBNQ_c_c&p
                //			='+pwd,
                //			//data : 'action=downprocess&sign=VTMAPgs6UG&p='+pwd,
                //			dataType : 'json',
                //			success:function(msg){
                //				var date = msg;
                //				if(date.zt == '1'){
                //					$("#downajax").html("<a href="+date.dom+"/file/"+ date.url +" target=_blank
                //					rel=noreferrer>下载</a>");
                //				}else{
                //					$("#info").text(date.inf);
                //				};
                //
                //			},
                // 匹配不到说明需要密码 直接解析并请求ajaxm.php

                // 匹配sign
                Pattern compile2 = Pattern.compile("sign=([0-9a-zA-Z_]{16,})");
                Matcher matcher2 = compile2.matcher(html);
                if (!matcher2.find()) {
                    return;
                }
                String sign = matcher2.group(1);

                var userAgent2 = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like " +
                        "Gecko) Chrome/111.0.0.0 Mobile Safari/537.36";

                HttpRequest<Buffer> bufferHttpRequest = client.postAbs(urlPrefix + "/ajaxm.php");
//                bufferHttpRequest.putHeader("User-Agent", userAgent2);
                bufferHttpRequest.putHeader("referer", key);
                bufferHttpRequest.sendForm(MultiMap.caseInsensitiveMultiMap()
                        .set("action", "downprocess")
                        .set("sign", sign).set("p", code)).onSuccess(res2 -> {
                    JsonObject urlJson = res2.bodyAsJsonObject();
                    System.out.println(urlJson);
                    System.out.println(urlJson.getString("dom")+"/file/"+urlJson.getString("url"));
                });

                return;
            }
            String iframePath = matcher.group(1);
            System.out.println(iframePath);
            client.getAbs(urlPrefix + iframePath).send().onSuccess(res2 -> {
                System.out.println(res2.bodyAsString());
            });
        });

    }
}
