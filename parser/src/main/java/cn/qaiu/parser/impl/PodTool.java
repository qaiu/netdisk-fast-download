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
                                        Pattern.compile("\"downloadUrl\":\"(?<url>https?://[^\s\"]+)").matcher(body);
                                if (matcher1.find()) {
                                    complete(matcher1.group("url"));
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

        // 正则表达式来匹配 URL
        String urlRegex = "'action'.+(?<url>https://.+)'\\)";
        Pattern urlPattern = Pattern.compile(urlRegex);
        Matcher urlMatcher = urlPattern.matcher(html);

        if (urlMatcher.find()) {
            String url = urlMatcher.group("url");
            System.out.println("URL: " + url);
            return url;
        }
        throw new RuntimeException("URL匹配失败");
    }


    private String matcherToken(String html) {
        // 正则表达式来匹配 inputElem.value 中的 Token
        String tokenRegex = "inputElem\\.value\\s*=\\s*'([^']+)'";
        Pattern tokenPattern = Pattern.compile(tokenRegex);
        Matcher tokenMatcher = tokenPattern.matcher(html);

        if (tokenMatcher.find()) {
            String token = tokenMatcher.group(1);
            System.out.println("Token: " + token);
            return token;
        }
        throw new RuntimeException("token匹配失败");
    }

    public Future<String> sendHttpRequest2(String token, String redeem) {
        Promise<String> promise = Promise.promise();
        // 构造 HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // 构造请求的 URI 和头部信息
        // https://onedrive.live.com/redir?cid=abfd0a26e47d3458&resid=ABFD0A26E47D3458!4465&ithint=file%2cxlsx&e=Ao2uSU&migratedtospo=true&redeem=aHR0cHM6Ly8xZHJ2Lm1zL3gvYy9hYmZkMGEyNmU0N2QzNDU4L0VWZzBmZVFtQ3YwZ2dLdHhFUUFBQUFBQlRQRWVDMTZfZk1EYk5FTjhEdTRta1E_ZT1BbzJ1U1U
        String url = ("https://my.microsoftpersonalcontent.com/_api/v2.0/shares/u!%s/driveItem?$select=content" +
                ".downloadUrl").formatted(redeem);
        String authorizationHeader = "Badger " + token;

        // 构建请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authorizationHeader)
                .build();

        // 发送请求并处理响应
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    System.out.println("Response Status Code: " + response.statusCode());
                    System.out.println("Response Body: " + response.body());
                    promise.complete(response.body());
                    return null;
                });

        return promise.future();
    }

    public Future<String> sendHttpRequest(String url, String token) {
        // 创建一个 WorkerExecutor 用于异步执行阻塞的 HTTP 请求
        WorkerExecutor executor = WebClientVertxInit.get().createSharedWorkerExecutor("http-client-worker");

        Promise<String> promise = Promise.promise();
        executor.executeBlocking(() -> {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = null;

            try {
                // 构造请求
                request = HttpRequest.newBuilder()
                        .uri(new URI(url))
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

                // 发起请求并获取响应
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // 返回响应体
                promise.complete(response.body());
                return null;
            } catch (URISyntaxException | IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        return promise.future();
    }
}
