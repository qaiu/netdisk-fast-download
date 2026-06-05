package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.FileSizeConverter;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.uritemplate.UriTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <a href="https://www.ctfile.com">诚通网盘</a>
 */
public class CtTool extends PanBase {
    private static final String API_URL_PREFIX = "https://webapi.ctfile.com";

    // https://webapi.ctfile.com/getfile.php?path=f&f=64115194-17569800420720-06c697&
    // passcode=7609&r=0.6611183001986635&ref=&url=https%3A%2F%2Furl94.ctfile.com%2Ff%2F64115194-17569800420720-06c697%3Fp%3D7609
    private static final String API1 = API_URL_PREFIX + "/getfile.php?path={path}" +
            "&f={shareKey}&passcode={pwd}&r={rand}&ref=&url={url}";

    // https://webapi.ctfile.com/get_file_url.php?uid=64115194&fid=17569800420720&folder_id=0&
    // share_id=&file_chk=af5c8757a49cbc69a557eb3da59b246c&start_time=1780471868&wait_seconds=0&
    // mb=0&app=0&acheck=1&verifycode=1780471868.2951fe63abedf36ec02f34ed5711ce70&rd=0.36350981353622636
    private static final String API2 = API_URL_PREFIX + "/get_file_url.php?" +
            "uid={uid}&fid={fid}&folder_id=0&share_id=&file_chk={file_chk}" +
            "&start_time={start_time}&wait_seconds={wait_seconds}&mb=0&app=0&acheck=1" +
            "&verifycode={verifycode}&rd={rand}";

    // https://webapi.ctfile.com/getdir.php?path=d&d=64115194-164803691-48508c&
    // folder_id=164803691&fk=decb36&passcode=7609&r=0.23...&ref=&url=https://url94.ctfile.com/d/...
    private static final String API_GETDIR = API_URL_PREFIX + "/getdir.php?path={path}" +
            "&d={shareKey}&folder_id={folder_id}&fk={fk}&passcode={pwd}&r={rand}&ref=&url={url}";

    // DataTables参数，用于获取目录文件列表
    private static final String FILE_LIST_PARAMS = "&sEcho=1&iColumns=4&sColumns=%2C%2C%2C" +
            "&iDisplayStart=0&iDisplayLength=500&mDataProp_0=0&mDataProp_1=1&mDataProp_2=2&mDataProp_3=3" +
            "&iSortCol_0=3&sSortDir_0=desc&iSortingCols=1";

    // 文件列表HTML解析正则
    private static final Pattern FILE_ID_PATTERN = Pattern.compile("value=\"f(\\d+)\"");
    private static final Pattern FILE_HREF_PATTERN = Pattern.compile("href=\"#/f/([^\"]+)\"");
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile(">([^<]+)</a>");
    private static final Pattern FILE_ICON_PATTERN = Pattern.compile("alt=\"([^\"]+)\"");

    /**
     * 子类重写此构造方法不需要添加额外逻辑
     * 如:
     * <blockquote><pre>
     *  public XxTool(String key, String pwd) {
     *      super(key, pwd);
     *  }
     * </pre></blockquote>
     *
     * @param shareLinkInfo 分享链接信息
     */
    public CtTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }


    @Override
    public Future<String> parse() {
        final String shareKey = shareLinkInfo.getShareKey();
        if (shareKey.indexOf('-') == -1) {
            fail("shareKey格式不正确找不到'-': {}", shareKey);
            return promise.future();
        }
        String[] split = shareKey.split("-");
        String uid = split[0], fid = split[1];
        // 获取url path
        int i1 = shareLinkInfo.getShareUrl().indexOf("com/");
        int i2 = shareLinkInfo.getShareUrl().lastIndexOf("/");
        String path = shareLinkInfo.getShareUrl().substring(i1 + 4, i2);

        HttpRequest<Buffer> bufferHttpRequest1 = clientSession.getAbs(UriTemplate.of(API1))
                .setTemplateParam("path", path)
                .setTemplateParam("shareKey", shareKey)
                .setTemplateParam("pwd", shareLinkInfo.getSharePassword())
                .setTemplateParam("rand", String.valueOf(Math.random()))
                .setTemplateParam("url", shareLinkInfo.getShareUrl());

        bufferHttpRequest1
                .send().onSuccess(res -> {
                    var resJson = asJson(res);
                    if (resJson.containsKey("file")) {
                        var fileJson = resJson.getJsonObject("file");
                        if (fileJson.containsKey("file_chk")) {
                            var file_chk = fileJson.getString("file_chk");
                            String startTime = fileJson.getValue("start_time").toString();
                            String waitSeconds = fileJson.getValue("wait_seconds").toString();
                            String verifycode = fileJson.getString("verifycode");

                            // 提取文件信息并存储
                            FileInfo fileInfo = new FileInfo()
                                    .setFileName(fileJson.getString("file_name"))
                                    .setFileId(String.valueOf(fileJson.getLong("file_id", 0L)))
                                    .setSizeStr(fileJson.getString("file_size"))
                                    .setCreateTime(fileJson.getString("file_time"))
                                    .setCreateBy(fileJson.getString("username"))
                                    .setFileType("file")
                                    .setPanType(shareLinkInfo.getType());
                            shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);

                            HttpRequest<Buffer> bufferHttpRequest2 = clientSession.getAbs(UriTemplate.of(API2))
                                    .setTemplateParam("uid", uid)
                                    .setTemplateParam("fid", fid)
                                    .setTemplateParam("file_chk", file_chk)
                                    .setTemplateParam("start_time", startTime)
                                    .setTemplateParam("wait_seconds", waitSeconds)
                                    .setTemplateParam("verifycode", verifycode)
                                    .setTemplateParam("rand", String.valueOf(Math.random()));
                            bufferHttpRequest2
                                    .send().onSuccess(res2 -> {
                                        JsonObject resJson2 = asJson(res2);
                                        if (resJson2.containsKey("downurl")) {
                                            String downloadUrl = resJson2.getString("downurl");

                                            // 存储下载元数据，包括必要的请求头
                                            Map<String, String> headers = new HashMap<>();
                                            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                                            headers.put("Referer", shareLinkInfo.getShareUrl());

                                            // 使用新的 completeWithMeta 方法
                                            completeWithMeta(downloadUrl, headers);
                                        } else {
                                            fail("解析失败, 可能分享已失效: json: {} 字段 {} 不存在", resJson2, "downurl");
                                        }
                                    }).onFailure(handleFail(bufferHttpRequest1.queryParams().toString()));
                        } else {
                            fail("解析失败, file_chk找不到, 可能分享已失效或者分享密码不对: {}", fileJson);
                        }
                    } else {
                        fail("解析失败, 文件信息为空, 可能分享已失效");
                    }
                }).onFailure(handleFail(bufferHttpRequest1.queryParams().toString()));
        return promise.future();
    }

    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> listPromise = Promise.promise();

        final String shareKey = shareLinkInfo.getShareKey();
        final String shareUrl = shareLinkInfo.getShareUrl();
        final String pwd = shareLinkInfo.getSharePassword();

        // shareKey格式: uid-folder_id-hash (例如 64115194-164803691-48508c)
        String[] split = shareKey.split("-");
        if (split.length < 2) {
            listPromise.fail(baseMsg() + " shareKey格式不正确: " + shareKey);
            return listPromise.future();
        }
        String folderId = split[1];

        // 从分享URL中提取fk参数
        String fk = extractQueryParam(shareUrl, "fk");

        // 从URL中提取path (例如从 "https://url94.ctfile.com/d/xxx?p=..." 中提取 "d")
        int comIdx = shareUrl.indexOf("com/");
        int qIdx = shareUrl.indexOf('?');
        String pathAndKey = qIdx > 0 ? shareUrl.substring(comIdx + 4, qIdx) : shareUrl.substring(comIdx + 4);
        int slashIdx = pathAndKey.indexOf('/');
        String path = slashIdx > 0 ? pathAndKey.substring(0, slashIdx) : pathAndKey;

        clientSession.getAbs(UriTemplate.of(API_GETDIR))
                .setTemplateParam("path", path)
                .setTemplateParam("shareKey", shareKey)
                .setTemplateParam("folder_id", folderId)
                .setTemplateParam("fk", fk != null ? fk : "")
                .setTemplateParam("pwd", pwd != null ? pwd : "")
                .setTemplateParam("rand", String.valueOf(Math.random()))
                .setTemplateParam("url", shareUrl)
                .send().onSuccess(res -> {
                    var resJson = asJson(res);
                    if (!resJson.containsKey("file")) {
                        listPromise.fail(baseMsg() + " 目录解析失败: " + resJson.encode());
                        return;
                    }
                    var dirInfo = resJson.getJsonObject("file");
                    String fileListRelUrl = dirInfo.getString("url");
                    if (fileListRelUrl == null) {
                        listPromise.fail(baseMsg() + " 文件列表URL为空");
                        return;
                    }

                    String fileListUrl = API_URL_PREFIX + fileListRelUrl + FILE_LIST_PARAMS;
                    clientSession.getAbs(fileListUrl)
                            .send().onSuccess(res2 -> {
                                var listJson = asJson(res2);
                                JsonArray aaData = listJson.getJsonArray("aaData");
                                if (aaData == null) {
                                    listPromise.fail(baseMsg() + " 文件列表为空");
                                    return;
                                }
                                List<FileInfo> fileList = new ArrayList<>();
                                String panType = shareLinkInfo.getType();
                                for (int i = 0; i < aaData.size(); i++) {
                                    var row = aaData.getJsonArray(i);
                                    try {
                                        String checkboxHtml = row.getString(0);
                                        String nameCellHtml = row.getString(1);
                                        String sizeStr = row.getString(2).trim();

                                        // 从checkbox HTML中提取文件ID
                                        String fileId = null;
                                        Matcher idMatcher = FILE_ID_PATTERN.matcher(checkboxHtml);
                                        if (idMatcher.find()) fileId = idMatcher.group(1);

                                        // 从文件名单元格HTML中提取临时分享key
                                        String fileShareKey = null;
                                        Matcher hrefMatcher = FILE_HREF_PATTERN.matcher(nameCellHtml);
                                        if (hrefMatcher.find()) fileShareKey = hrefMatcher.group(1);

                                        // 提取文件名
                                        String fileName = null;
                                        Matcher nameMatcher = FILE_NAME_PATTERN.matcher(nameCellHtml);
                                        if (nameMatcher.find()) fileName = nameMatcher.group(1).trim();

                                        // 提取文件图标/类型
                                        String fileIcon = null;
                                        Matcher iconMatcher = FILE_ICON_PATTERN.matcher(nameCellHtml);
                                        if (iconMatcher.find()) fileIcon = iconMatcher.group(1);

                                        if (fileName == null || fileShareKey == null) continue;

                                        long sizeBytes = 0;
                                        try {
                                            sizeBytes = FileSizeConverter.convertToBytes(sizeStr);
                                        } catch (Exception ignored) {}

                                        FileInfo fileInfo = new FileInfo()
                                                .setFileName(fileName)
                                                .setFileId(fileId)
                                                .setSizeStr(sizeStr)
                                                .setSize(sizeBytes)
                                                .setFileType(fileIcon)
                                                .setFileIcon(fileIcon)
                                                .setPanType(panType)
                                                .setParserUrl(String.format("%s/v2/redirectUrl/%s/%s",
                                                        getDomainName(), panType, fileShareKey));
                                        fileList.add(fileInfo);
                                    } catch (Exception e) {
                                        log.warn("解析文件行失败: {}", e.getMessage());
                                    }
                                }
                                listPromise.complete(fileList);
                            }).onFailure(listPromise::fail);
                }).onFailure(listPromise::fail);

        return listPromise.future();
    }

    private String extractQueryParam(String url, String paramName) {
        if (url == null) return null;
        int qIdx = url.indexOf('?');
        if (qIdx < 0) return null;
        String query = url.substring(qIdx + 1);
        for (String param : query.split("&")) {
            int eqIdx = param.indexOf('=');
            if (eqIdx > 0 && param.substring(0, eqIdx).equals(paramName)) {
                return param.substring(eqIdx + 1);
            }
        }
        return null;
    }
}
