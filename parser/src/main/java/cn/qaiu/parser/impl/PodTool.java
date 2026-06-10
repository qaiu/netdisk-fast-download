package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <a href="https://onedrive.live.com/">onedrive分享(od)</a>
 */
public class PodTool extends PanBase {

    private static final int MAX_RESPONSE_BODY_BYTES = 8 * 1024 * 1024;
    private static final java.time.Duration REQUEST_TIMEOUT = java.time.Duration.ofSeconds(30);

    // 静态共享的 JDK HttpClient 实例，避免每次调用创建新实例
    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    private static volatile WorkerExecutor SHARED_WORKER_EXECUTOR;
    private static volatile boolean workerExecutorShutdown = false;

    /*
     * https://1drv.ms/w/s!Alg0feQmCv2rnRFd60DQOmMa-Oh_?e=buaRtp --302->
     * https://api.onedrive.com/v1.0/drives/abfd0a26e47d3458/items/ABFD0A26E47D3458!3729?authkey=!AF3rQNA6Yxr46H8
     * https://onedrive.live.com/redir?resid=(?<cid>)!(?<cid2>)&authkey=(?<authkey>)&e=hV98W1
     * cid: abfd0a26e47d3458, cid2: ABFD0A26E47D3458!3729 authkey: !AF3rQNA6Yxr46H8
     * -> @content.downloadUrl
     */


    // https://onedrive.live.com/redir?resid=ABFD0A26E47D3458!4699&e=OggA4s&migratedtospo=true&redeem=aHR0cHM6Ly8xZHJ2Lm1zL3UvcyFBbGcwZmVRbUN2MnJwRnZ1NDQ0aGc1eVZxRGNLP2U9T2dnQTRz
    private static final String API_TEMPLATE = "https://onedrive.live.com/embed" +
            "?id={resid}&resid={resid1}" +
            "&cid={cid}" +
            "&redeem={redeem}" +
            "&migratedtospo=true&embed=1";

    private static final String TOKEN_API = "https://api-badgerp.svc.ms/v1.0/token";


    private static final Pattern redirectUrlRegex =
            Pattern.compile("resid=(?<cid1>[^!]+)!(?<cid2>[^&]+).+&redeem=(?<redeem>.+).*");

    private static final Pattern DOWNLOAD_URL_IN_RESPONSE_PATTERN =
            Pattern.compile("\"downloadUrl\":\"(?<url>https?://[^\\s\"]+)");
    private static final Pattern ACTION_URL_PATTERN =
            Pattern.compile("'action'.+(?<url>https://.+)'\\)");
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("inputElem\\.value\\s*=\\s*'([^']+)'");

    public PodTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {


        /*
         * POST https://api-badgerp.svc.ms/v1.0/token
         * Content-Type: application/json
         *
         * {
         *   "appid": "00000000-0000-0000-0000-0000481710a4"
         * }
         */
        // https://my.microsoftpersonalcontent.com/_api/v2.0/shares/u!aHR0cHM6Ly8xZHJ2Lm1zL3UvcyFBbGcwZmVRbUN2MnJwRnZ1NDQ0aGc1eVZxRGNLP2U9T2dnQTRz/driveitem?%24select=*%2Cocr%2CwebDavUrl
        // https://onedrive.live.com/embed?id=ABFD0A26E47D3458!4698&resid=ABFD0A26E47D3458!4698&cid=abfd0a26e47d3458&redeem=aHR0cHM6Ly8xZHJ2Lm1zL3UvYy9hYmZkMGEyNmU0N2QzNDU4L0lRUllOSDNrSmdyOUlJQ3JXaElBQUFBQUFTWGlubWZ2WmNxYUQyMXJUQjIxVmg4&migratedtospo=true&embed=1


        clientNoRedirects.getAbs(shareLinkInfo.getShareUrl() == null ? shareLinkInfo.getStandardUrl() :
                shareLinkInfo.getShareUrl()).send().onSuccess(r0 -> {
            String location = r0.getHeader("Location");
            Matcher matcher = redirectUrlRegex.matcher(location);
            if (!matcher.find()) {
                fail("Location格式错误");
                return;
            }
            String redeem = matcher.group("redeem");
            String cid1 = matcher.group("cid1");
            String cid2 = cid1 + "!" + matcher.group("cid2");

            clientNoRedirects.getAbs(UriTemplate.of(API_TEMPLATE))
                    .setTemplateParam("resid", cid2)
                    .setTemplateParam("resid1", cid2)
                    .setTemplateParam("cid", cid1.toLowerCase())
                    .setTemplateParam("redeem", redeem)
                    .send()
                    .onSuccess(r1 -> {
                        String auth =
                                r1.cookies().stream().filter(c -> c.startsWith("BadgerAuth=")).findFirst().orElse("");
                        if (auth.isEmpty()) {
                            fail("Error BadgerAuth not fount");
                            return;
                        }
                        String token = auth.split(";")[0].split("=")[1];

                        try {

                            String url = matcherUrl(r1.bodyAsString());

                            sendHttpRequest(url, token).onSuccess(body -> {
                                Matcher matcher1 =
                                        DOWNLOAD_URL_IN_RESPONSE_PATTERN.matcher(body);
                                if (matcher1.find()) {
                                    // 响应体是 JSON 文本，URL 中的 '&' 被转义为 \u0026，需要反转义
                                    complete(unescapeJsonUnicode(matcher1.group("url")));
                                } else {
                                    fail();
                                }
                            }).onFailure(handleFail());
                        } catch (Exception ignored) {
                            sendHttpRequest2(token, redeem).onSuccess(res -> {
                                try {
                                    complete(new JsonObject(res).getString("@content.downloadUrl"));
                                } catch (Exception ignored1) {
                                    fail();
                                }
                            }).onFailure(handleFail());
                        }

                    }).onFailure(handleFail());
        }).onFailure(handleFail());
        return promise.future();
    }

    private String matcherUrl(String html) {
        Matcher urlMatcher = ACTION_URL_PATTERN.matcher(html);

        if (urlMatcher.find()) {
            String url = urlMatcher.group("url");
            log.debug("URL: {}", url);
            return url;
        }
        throw new RuntimeException("URL匹配失败");
    }

    /**
     * 反转义 JSON 响应文本中残留的 Unicode 转义序列（主要是 \u0026 -> &）。
     * 主分支通过正则直接从 JSON 原文抠 URL，未经过 JSON 解析器，需要手动还原。
     */
    private String unescapeJsonUnicode(String s) {
        if (s == null || s.indexOf("\\u") < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 5 < s.length() && s.charAt(i + 1) == 'u') {
                try {
                    int cp = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                    sb.append((char) cp);
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {
                    // 非法转义按原样保留
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }


    private String matcherToken(String html) {
        Matcher tokenMatcher = TOKEN_PATTERN.matcher(html);

        if (tokenMatcher.find()) {
            String token = tokenMatcher.group(1);
            log.debug("Token: {}***", token.length() > 4 ? token.substring(0, 4) : "***");
            return token;
        }
        throw new RuntimeException("token匹配失败");
    }

    public Future<String> sendHttpRequest2(String token, String redeem) {
        Promise<String> promise = Promise.promise();

        // 构造请求的 URI 和头部信息
        String url = ("https://my.microsoftpersonalcontent.com/_api/v2.0/shares/u!%s/driveItem?$select=content" +
                ".downloadUrl").formatted(redeem);
        String authorizationHeader = "Badger " + token;

        // 构建请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authorizationHeader)
                .build();

        // 发送请求并处理响应（使用共享的 HttpClient）
        SHARED_HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    log.debug("Response Status Code: {}", response.statusCode());
                    promise.complete(toLimitedString(response.body()));
                    return null;
                })
                .exceptionally(e -> {
                    log.error("sendHttpRequest2 请求失败: {}", e.getMessage());
                    promise.fail(e);
                    return null;
                });

        return promise.future();
    }

    public Future<String> sendHttpRequest(String url, String token) {
        Promise<String> promise = Promise.promise();
        getWorkerExecutor().executeBlocking(() -> {
            try {
                // 构造请求
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .timeout(REQUEST_TIMEOUT)
                        .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9," +
                                "image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;" +
                                "v=b3;q=0.7")
                        .header("accept-language", "zh-CN,zh;q=0.9")
                        .header("cache-control", "no-cache")
                        .header("content-type", "application/x-www-form-urlencoded")
                        .header("dnt", "1")
                        .header("origin", "https://onedrive.live.com")
                        .header("pragma", "no-cache")
                        .header("priority", "u=0, i")
                        .header("referer", "https://onedrive.live.com/")
                        .header("sec-ch-ua", "\"Chromium\";v=\"130\", \"Google Chrome\";v=\"130\", " +
                                "\"Not?A_Brand\";v=\"99\"")
                        .header("sec-ch-ua-mobile", "?0")
                        .header("sec-ch-ua-platform", "\"Windows\"")
                        .header("sec-fetch-dest", "iframe")
                        .header("sec-fetch-mode", "navigate")
                        .header("sec-fetch-site", "cross-site")
                        .header("upgrade-insecure-requests", "1")
                        .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537" +
                                ".36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                        .POST(HttpRequest.BodyPublishers.ofString("badger_token=" + token))
                        .build();

                // 发起请求并获取响应（使用共享的 HttpClient）
                HttpResponse<byte[]> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

                // 返回响应体
                promise.complete(toLimitedString(response.body()));
                return null;
            } catch (URISyntaxException | IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new RuntimeException(e);
            }
        }).onFailure(promise::fail);

        return promise.future();
    }

    private static String toLimitedString(byte[] body) {
        if (body.length > MAX_RESPONSE_BODY_BYTES) {
            throw new IllegalArgumentException("OneDrive响应体过大: " + body.length + " bytes");
        }
        return new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static WorkerExecutor getWorkerExecutor() {
        synchronized (PodTool.class) {
            if (workerExecutorShutdown) {
                throw new IllegalStateException("OneDrive WorkerExecutor 已关闭");
            }
            if (SHARED_WORKER_EXECUTOR == null) {
                SHARED_WORKER_EXECUTOR = WebClientVertxInit.get().createSharedWorkerExecutor("http-client-worker", 8);
            }
            return SHARED_WORKER_EXECUTOR;
        }
    }

    public static void shutdownWorkerExecutor() {
        synchronized (PodTool.class) {
            workerExecutorShutdown = true;
            if (SHARED_WORKER_EXECUTOR != null) {
                SHARED_WORKER_EXECUTOR.close();
                SHARED_WORKER_EXECUTOR = null;
            }
        }
    }
}
