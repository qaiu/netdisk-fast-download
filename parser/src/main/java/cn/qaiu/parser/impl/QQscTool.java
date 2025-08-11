package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.HeaderUtils;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ闪传 <br>
 * 只能客户端上传 支持Android QQ 9.2.5, MACOS QQ 6.9.78，可生成分享链接，通过浏览器下载，支持超大文件，有效期默认7天（暂时没找到续期方法）。<br>
 */
public class QQscTool extends PanBase {

    Logger LOG = LoggerFactory.getLogger(QQscTool.class);

    private static final String API_URL = "https://qfile.qq.com/http2rpc/gotrpc/noauth/trpc.qqntv2.richmedia.InnerProxy/BatchDownload";

    private static final MultiMap HEADERS = HeaderUtils.parseHeaders("""
            Accept-Encoding: gzip, deflate
            Accept-Language: zh-CN,zh;q=0.9
            Connection: keep-alive
            Cookie: uin=9000002; p_uin=9000002
            DNT: 1
            Origin: https://qfile.qq.com
            Referer: https://qfile.qq.com/q/Xolxtv5b4O
            Sec-Fetch-Dest: empty
            Sec-Fetch-Mode: cors
            Sec-Fetch-Site: same-origin
            User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Edg/138.0.0.0
            accept: application/json
            content-type: application/json
            sec-ch-ua: "Not)A;Brand";v="8", "Chromium";v="138", "Microsoft Edge";v="138"
            sec-ch-ua-mobile: ?0
            sec-ch-ua-platform: "macOS"
            x-oidb: {"uint32_command":"0x9248", "uint32_service_type":"4"}
            """);

    public QQscTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        String jsonTemplate = """
                {"req_head":{"agent":8},"download_info":[{"batch_id":"%s","scene":{"business_type":4,"app_type":22,"scene_type":5},"index_node":{"file_uuid":"%s"},"url_type":2,"download_scene":0}],"scene_type":103}
                """;

        client.getAbs(shareLinkInfo.getShareUrl()).send(result -> {
            if (result.succeeded()) {
                String htmlJs = result.result().bodyAsString();
                LOG.debug("获取到的HTML内容: {}", htmlJs);
                String fileUUID = getFileUUID(htmlJs);
                String fileName = extractFileNameFromTitle(htmlJs);
                if (fileName != null) {
                    LOG.info("提取到的文件名: {}", fileName);
                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setFileName(fileName);
                    shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
                } else {
                    LOG.warn("未能提取到文件名");
                }
                if (fileUUID != null) {
                    LOG.info("提取到的文件UUID: {}", fileUUID);
                    String formatted = jsonTemplate.formatted(fileUUID, fileUUID);
                    JsonObject entries = new JsonObject(formatted);
                    client.postAbs(API_URL)
                            .putHeaders(HEADERS)
                            .sendJsonObject(entries)
                            .onSuccess(result2 -> {
                                if (result2.statusCode() == 200) {
                                    JsonObject body = asJson(result2);
                                    LOG.debug("API响应内容: {}", body.encodePrettily());
                                    // {
                                    //	"retcode": 0,
                                    //	"cost": 132,
                                    //	"message": "",
                                    //	"error": {
                                    //		"message": "",
                                    //		"code": 0
                                    //	},
                                    //	"data": {
                                    //		"download_rsp": [{

                                    // 取 download_rsp
                                    if (!body.containsKey("retcode") || body.getInteger("retcode") != 0) {
                                        promise.fail("API请求失败，错误信息: " + body.encodePrettily());
                                        return;
                                    }
                                    JsonArray downloadRsp = body.getJsonObject("data").getJsonArray("download_rsp");
                                    if (downloadRsp != null && !downloadRsp.isEmpty()) {
                                        String url = downloadRsp.getJsonObject(0).getString("url");
                                        if (fileName != null) {
                                            url = url + "&filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                                        }
                                        promise.complete(url);
                                    } else {
                                        promise.fail("API响应中缺少 download_rsp");
                                    }
                                } else {
                                    promise.fail("API请求失败，状态码: " + result2.statusCode());
                                }
                            }).onFailure(e -> {
                                LOG.error("API请求异常", e);
                                promise.fail(e);
                            });
                } else {
                    LOG.error("未能提取到文件UUID");
                    promise.fail("未能提取到文件UUID");
                }
            } else {
                LOG.error("请求失败: {}", result.cause().getMessage());
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }

    String getFileUUID(String htmlJs) {
        String keyword = "\"download_limit_status\"";
        String marker = "},\"";

        int startIndex = htmlJs.indexOf(keyword);
        if (startIndex != -1) {
            int markerIndex = htmlJs.indexOf(marker, startIndex);
            if (markerIndex != -1) {
                int quoteStart = markerIndex + marker.length();
                int quoteEnd = htmlJs.indexOf("\"", quoteStart);
                if (quoteEnd != -1) {
                    String extracted = htmlJs.substring(quoteStart, quoteEnd);
                    LOG.debug("提取结果: {}", extracted);
                    return extracted;
                } else {
                    LOG.error("未找到结束引号: {}", marker);
                }
            } else {
                LOG.error("未找到标记: {} 在关键字: {} 之后", marker, keyword);
            }
        } else {
            LOG.error("未找到关键字: {}", keyword);
        }
        return null;
    }

    public static String extractFileNameFromTitle(String content) {
        // 匹配<title>和</title>之间的内容
        Pattern pattern = Pattern.compile("<title>(.*?)</title>");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String fullTitle = matcher.group(1);
            // 按 "｜" 分割，取前半部分
            int sepIndex = fullTitle.indexOf("｜");
            if (sepIndex != -1) {
                return fullTitle.substring(0, sepIndex);
            }
            return fullTitle; // 如果没有分隔符，就返回全部
        }
        return null;
    }
}

