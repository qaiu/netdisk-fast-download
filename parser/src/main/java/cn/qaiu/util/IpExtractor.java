package cn.qaiu.util;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

public class IpExtractor {
    public static void main(String[] args) throws InterruptedException {


        // 创建请求头Map
        MultiMap headers = new HeadersMultiMap();
        headers.add("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        headers.add("accept-language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.add("cache-control", "no-cache");
        headers.add("dnt", "1");
        headers.add("origin", "https://ip.ihuan.me");
        headers.add("pragma", "no-cache");
        headers.add("priority", "u=0, i");
        headers.add("referer", "https://ip.ihuan.me/ti.html");
        headers.add("sec-ch-ua", "\"Google Chrome\";v=\"129\", \"Not=A?Brand\";v=\"8\", \"Chromium\";v=\"129\"");
        headers.add("sec-ch-ua-mobile", "?0");
        headers.add("sec-ch-ua-platform", "\"Windows\"");
        headers.add("sec-fetch-dest", "document");
        headers.add("sec-fetch-mode", "navigate");
        headers.add("sec-fetch-site", "same-origin");
        headers.add("sec-fetch-user", "?1");
        headers.add("upgrade-insecure-requests", "1");
        headers.add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36");
        headers.add("Content-Type", "application/x-www-form-urlencoded");

        WebClient client = WebClient.create(Vertx.vertx());
        WebClientSession webClientSession = WebClientSession.create(client);
        webClientSession.getAbs("https://ip.ihuan.me").putHeaders(headers).send().onSuccess(res->{
            System.out.println(res.toString());
            webClientSession.getAbs("https://ip.ihuan.me").putHeaders(headers).send().onSuccess(res2->{
                System.out.println(res2.toString());

            });
        });
//
//        String htmlContent = "<div class=\"panel-heading\">提取结果</div>\n" +
//                "<div class=\"panel-body\">111.1.27.85:80<br>42.63.65.46:80<br>118.195.242.20:8080<br>42.63.65.119:80<br>117.50.108.90:7890<br>116.62.50.250:7890<br>114.231.8.177:8089<br>190.43.92.90:999<br>221.178.85.68:8080<br><br>61.160.202.86:80<br>42.63.65.86:80<br>42.63.65.7:80<br>42.63.65.41:80<br>159.226.227.119:80<br>61.160.202.52:80<br>42.63.65.15:80<br>112.17.16.204:80<br>61.160.202.53:80<br>42.63.65.9:80<br>42.63.65.60:80<br>42.63.65.18:80<br>203.190.115.177:8071<br>42.63.65.38:80<br>42.63.65.31:80<br>91.185.3.126:8080<br>139.9.119.20:80<br>1.15.47.213:443<br>183.164.243.108:8089<br>165.225.208.177:80<br>194.163.132.232:3128<br>91.235.220.122:80<br>39.100.120.200:7890<br>141.147.33.121:80<br>183.164.243.138:8089<br>104.129.205.94:54321<br>117.160.250.138:81<br>180.120.213.208:8089<br>61.130.9.37:443<br>182.34.18.206:9999<br>117.86.12.150:8089<br>27.192.173.108:9000<br>183.164.243.11:8089<br>114.231.41.205:8089<br>103.104.233.78:8080<br>183.164.243.174:8089<br>36.6.144.230:8089<br>111.224.213.239:8089<br>182.92.73.106:80<br>36.6.145.81:8089<br>117.69.232.125:8089<br>36.6.144.64:8089<br>117.57.92.20:8089<br>47.100.69.29:8888<br>117.57.93.246:8089<br>120.234.203.171:9002<br>114.231.46.231:8089<br>183.164.242.199:8089<br>117.69.237.179:8089<br>182.44.32.239:7890<br>47.100.91.57:8080<br>117.69.236.127:8089<br>114.231.8.18:8089<br>117.69.232.183:8089<br>117.69.237.29:8089<br>183.164.242.67:8089<br>183.164.242.35:8089<br>183.164.243.71:8089<br>113.223.214.155:8089<br>36.6.145.132:8089<br>182.106.220.252:9091<br>113.223.212.176:8089<br>62.152.53.186:8909<br>117.57.92.16:8089<br>183.164.243.186:8089<br>36.6.144.210:8089<br>183.164.242.189:8089<br>213.178.39.170:8080<br>121.52.145.163:8080<br>36.6.144.240:8089<br>60.188.5.234:80<br>113.223.213.48:8089<br>183.164.243.149:8089<br>200.58.87.195:8080<br>36.6.144.153:8089<br>36.6.144.67:8089<br>36.6.145.182:8089<br>117.57.93.226:8089<br>42.112.24.127:8888<br>43.138.20.156:80<br>117.57.92.79:8089<br>65.109.111.238:3128<br>183.166.137.201:41122<br>113.223.213.150:8089<br>36.6.145.154:8089<br>185.5.209.101:80<br>36.6.144.17:8089<br>114.231.8.244:8089<br>117.69.237.24:8089<br>117.69.236.232:8089<br>117.69.236.127:8089<br>114.231.8.18:8089<br>117.69.232.183:8089<br>117.69.237.29:8089<br>183.164.242.67:8089<br>183.164.242.35:8089<br>183.164.243.71:8089<br>113.223.214.155:8089<br>36.6.145.132:8089<br>182.106.220.252:9091<br>113.223.212.176:8089<br>62.152.53.186:8909<br>117.57.92.16:8089<br>183.164.243.186:8089<br>36.6.144.210:8089<br>183.164.242.189:8089<br>213.178.39.170:8080<br>121.52.145.163:8080<br>36.6.144.240:8089<br>60.188.5.234:80<br>113.223.213.48:8089<br>183.164.243.149:8089<br>200.58.87.195:8080<br>36.6.144.153:8089<br>36.6.144.67:8089<br>36.6.145.182:8089<br>117.57.93.226:8089<br>42.112.24.127:8888<br>43.138.20.156:80<br>117.57.92.79:8089<br>65.109.111.238:3128<br>183.166.137.201:41122<br>113.223.213.150:8089<br>36.6.145.154:8089<br>185.5.209.101:80<br>36.6.144.17:8089<br>114.231.8.244:8089<br>117.69.237.24:8089<br>117.69.236.232:8089</div>";
//
//        // 正则表达式匹配提取结果关键字下面的IP地址
//        Pattern pattern = Pattern.compile("<div class=\"panel-heading\">提取结果</div>\\s*<div class=\"panel-body\">([\\s\\S]*?)(?=</div>)", Pattern.DOTALL);
//        Matcher matcher = pattern.matcher(htmlContent);
//
//        if (matcher.find()) {
//            String ipText = matcher.group(1); // 获取匹配的IP地址部分
//            Pattern ipPattern = Pattern.compile("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::\\d+)?");
//            Matcher ipMatcher = ipPattern.matcher(ipText);
//
//            List<String> ips = new ArrayList<>();
//            while (ipMatcher.find()) {
//                ips.add(ipMatcher.group());
//            }
//
//            System.out.println("提取到的IP地址：");
//            for (String ip : ips) {
////                System.out.println(ip);
//            }
//        } else {
//            System.out.println("没有找到匹配的IP地址");
//        }
//
//        TimeUnit.SECONDS.sleep(1000);
    }
}

