package cn.qaiu.lz.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author QAIU
 * @version 1.0 update 2021/5/16 10:39
 */
public class LzTool {

    public static String parse(String fullUrl) throws Exception {
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.3";
        String url = fullUrl.substring(0, fullUrl.lastIndexOf('/') + 1);
        String id = fullUrl.substring(fullUrl.lastIndexOf('/') + 1);
        Map<String, String> header = new HashMap<>();
        header.put("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
        header.put("referer", url);


        /*
            // 部分链接需要设置安卓UA
            sec-ch-ua: "Google Chrome";v="111", "Not(A:Brand";v="8", "Chromium";v="111"
            sec-ch-ua-mobile: ?1
            sec-ch-ua-platform: "Android"
         */
        String userAgent2 = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Mobile Safari/537.36";

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
        System.out.println(result);
        Matcher matcher = Pattern.compile("'[\\w]+_c_c'").matcher(result);
        Map<String, String> params = new LinkedHashMap<>();
        if (matcher.find()) {
            String sn = matcher.group().replace("'", "");
            params.put("action", "downprocess");
            params.put("sign", sn);
            params.put("ves", "1");
            System.out.println(sn);

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
        params = new ObjectMapper().readValue(result, new TypeReference<Map<String, String>>() {});
        System.out.println(params);
        //通过json的数据拼接出最终的URL发起第最终请求,并得到响应信息头
        url = params.get("dom") + "/file/" + params.get("url");
        Map<String, String> headers = Jsoup.connect(url)
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
}
