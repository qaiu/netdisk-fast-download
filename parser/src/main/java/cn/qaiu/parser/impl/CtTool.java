package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.parser.impl.CtfileSession.CtfileException;
import cn.qaiu.parser.impl.CtfileSession.DirectLink;
import cn.qaiu.parser.impl.CtfileSession.ShareTarget;
import cn.qaiu.parser.impl.CtfileSession.VerificationMismatch;
import cn.qaiu.util.CommonUtils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 城通网盘。分享页已经改成 {@code api.ctfile.com/url/*}：
 * 免密分享先解 ALTCHA v2，再取 {@code access_pass} 和下载地址；
 * 带密码分享由 Cloudflare Turnstile 拦住，服务端解不开。
 */
public class CtTool extends PanBase {

    private static final String SHARE_FILE_URL_PREFIX = "https://ctfile.com/file/";
    private static final int DEFAULT_MAX_LIST_PAGES = 50;

    private static final Object WORKER_LOCK = new Object();
    private static volatile WorkerExecutor sharedWorker;
    private static volatile boolean workerShutdown;

    public CtTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public static void shutdownWorkerExecutor() {
        synchronized (WORKER_LOCK) {
            workerShutdown = true;
            if (sharedWorker != null) {
                sharedWorker.close();
                sharedWorker = null;
            }
        }
    }

    @Override
    public Future<String> parse() {
        String shareKey = shareLinkInfo.getShareKey();
        if (shareKey == null || shareKey.isBlank()) {
            fail("shareKey为空");
            return promise.future();
        }
        try {
            worker().executeBlocking(() -> {
                ParsedDownload link = resolveDownload(shareKey);
                shareLinkInfo.getOtherParam().put("fileInfo", fileInfoFrom(link.file));
                if (link.fileDir != null && !link.fileDir.isBlank()) {
                    shareLinkInfo.getOtherParam().put("ctFileDir", link.fileDir);
                }
                return link;
            }, false).onSuccess(link -> {
                if (link.headers == null || link.headers.isEmpty()) {
                    complete(link.url);
                } else {
                    completeWithMeta(link.url, link.headers);
                }
            }).onFailure(error -> fail("{}", rootMessage(error)));
        } catch (RuntimeException e) {
            fail("{}", e.getMessage());
        }
        return promise.future();
    }

    @Override
    public Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> listPromise = Promise.promise();
        String shareKey = shareLinkInfo.getShareKey();
        if (shareKey == null || shareKey.isBlank()) {
            listPromise.fail(baseMsg() + " - 解析异常: shareKey为空");
            return listPromise.future();
        }
        try {
            worker().executeBlocking(() -> listDirectory(shareKey), false)
                    .onSuccess(listPromise::complete)
                    .onFailure(error -> {
                        if (!listPromise.future().isComplete()) {
                            listPromise.fail(baseMsg() + " - 解析异常: " + rootMessage(error));
                        }
                    });
        } catch (RuntimeException e) {
            listPromise.fail(baseMsg() + " - 解析异常: " + e.getMessage());
        }
        return listPromise.future();
    }

    @Override
    public Future<String> parseById() {
        Object paramValue = shareLinkInfo.getOtherParam().get("paramJson");
        if (!(paramValue instanceof JsonObject)) {
            fail("缺少下载参数paramJson");
            return promise.future();
        }
        if (!applyFileParam(shareLinkInfo, (JsonObject) paramValue)) {
            fail("下载参数id为空");
            return promise.future();
        }
        return parse();
    }

    static boolean applyFileParam(ShareLinkInfo shareLinkInfo, JsonObject paramJson) {
        String fileShareKey = paramJson.getString("id");
        if (fileShareKey == null || fileShareKey.isBlank()) {
            return false;
        }
        shareLinkInfo.setSharePassword(paramJson.getString("pwd", ""));
        shareLinkInfo.setShareKey(fileShareKey);
        shareLinkInfo.setShareUrl(SHARE_FILE_URL_PREFIX + fileShareKey);
        shareLinkInfo.setStandardUrl(SHARE_FILE_URL_PREFIX + fileShareKey);
        return true;
    }

    private ParsedDownload resolveDownload(String shareKey) throws Exception {
        VerificationMismatch mismatch = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try (CtfileSession session = CtfileSession.open(shareLinkInfo)) {
                try {
                    return downloadOnce(session, shareKey);
                } catch (VerificationMismatch retryable) {
                    // verifycode 和签发它的那次连接绑在一起。换一个客户端再取一次。
                    mismatch = retryable;
                }
            }
        }
        throw mismatch == null ? new CtfileException("未返回下载地址") : mismatch;
    }

    private ParsedDownload downloadOnce(CtfileSession session, String shareKey) throws Exception {
        String password = shareLinkInfo.getSharePassword();
        ShareTarget target = ShareTarget.from(shareLinkInfo);
        JsonObject resource = session.loadResource("file", shareKey, target, password, turnstileToken());
        JsonObject data = CtfileSession.dataOf(resource);
        JsonObject file = data == null ? null : data.getJsonObject("file");
        if (file == null) {
            throw new CtfileException("下载参数不完整：上游没有返回文件信息");
        }
        DirectLink link = session.download(shareKey, target, password, resource);
        return new ParsedDownload(link.url, link.headers, file, file.getString("file_dir", ""));
    }

    private List<FileInfo> listDirectory(String shareKey) throws Exception {
        String password = shareLinkInfo.getSharePassword();
        ShareTarget target = ShareTarget.from(shareLinkInfo);
        int maxPages = maxListPages();
        try (CtfileSession session = CtfileSession.open(shareLinkInfo)) {
            JsonObject resource = session.loadResource("dir", shareKey, target, password, turnstileToken());
            List<FileInfo> files = new ArrayList<>();
            int start = 0;
            for (int page = 0; page < maxPages; page++) {
                JsonObject listing = session.listing("dir", shareKey, target, password, resource, start,
                        CtfileSession.pageSize());
                int code = CtfileSession.intValue(listing.getValue("code"), 200);
                if (code != 200) {
                    throw new CtfileException(CtfileSession.classify(listing, code, password).message);
                }
                JsonArray rows = CtfileSession.rowsOf(listing);
                for (int i = 0; i < rows.size(); i++) {
                    if (rows.getValue(i) instanceof JsonObject row) {
                        FileInfo info = toFileInfo(row, shareLinkInfo);
                        if (info != null) {
                            files.add(info);
                        }
                    }
                }
                if (rows.isEmpty() || !CtfileSession.hasMore(listing, rows.size())) {
                    return files;
                }
                start += rows.size();
            }
            if (maxPages != DEFAULT_MAX_LIST_PAGES || shareLinkInfo.getOtherParam().containsKey("ctMaxListPages")) {
                return files;
            }
            throw new CtfileException("目录文件过多，已超过 " + maxPages + " 页");
        }
    }

    static FileInfo toFileInfo(JsonObject row, ShareLinkInfo info) {
        if (row == null) {
            return null;
        }
        String name = row.getString("name", "").trim();
        if (name.isBlank()) {
            return null;
        }
        String kind = row.getString("kind", "");
        String password = info.getSharePassword() == null ? "" : info.getSharePassword();
        FileInfo fileInfo = new FileInfo()
                .setFileName(name)
                .setPanType(info.getType())
                .setCreateBy(CtfileSession.text(row.getValue("ownerId")));
        String date = row.getString("date", "");
        if (!date.isBlank()) {
            fileInfo.setCreateTime(date).setUpdateTime(date);
        }
        if ("folder".equals(kind)) {
            String folderId = CtfileSession.text(row.getValue("folderId"));
            if (folderId.isBlank()) {
                return null;
            }
            String folderKey = row.getString("folderKey", "");
            fileInfo.setFileId(folderId)
                    .setFileType("folder")
                    .setSize(0L)
                    .setSizeStr("0B")
                    .setParserUrl(buildFolderParserUrl(info, folderId + ":" + (folderKey == null ? "" : folderKey), password));
            return fileInfo;
        }
        if (!"file".equals(kind)) {
            return null;
        }
        String fileKey = fileKeyFromHref(row.getString("href", ""));
        if (fileKey.isBlank()) {
            fileKey = CtfileSession.text(row.getValue("fileId"));
        }
        if (fileKey.isBlank()) {
            return null;
        }
        long size = CtfileSession.longValue(row.getValue("size_bytes"), 0L);
        JsonObject param = new JsonObject()
                .put("id", fileKey)
                .put("fileName", name)
                .put("pwd", password);
        String encoded = CommonUtils.urlBase64Encode(param.encode());
        String domain = info.getOtherParam() == null ? "" : info.getOtherParam().getOrDefault("domainName", "").toString();
        fileInfo.setFileId(CtfileSession.firstNonBlank(CtfileSession.text(row.getValue("fileId")), fileKey))
                .setFileType(CtfileSession.firstNonBlank(row.getString("extension"), "file"))
                .setSize(size)
                .setSizeStr(row.getString("size", ""))
                .setParserUrl(domain + "/v2/redirectUrl/" + info.getType() + "/" + encoded);
        return fileInfo;
    }

    private static String buildFolderParserUrl(ShareLinkInfo info, String dirId, String password) {
        String domain = info.getOtherParam() == null ? "" : info.getOtherParam().getOrDefault("domainName", "").toString();
        String shareUrl = info.getShareUrl() == null ? "" : info.getShareUrl();
        String url = domain + "/v2/getFileList?url=" + urlEncode(shareUrl) + "&dirId=" + urlEncode(dirId);
        if (password != null && !password.isBlank()) {
            url += "&pwd=" + urlEncode(password);
        }
        return url;
    }

    private static String fileKeyFromHref(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        int marker = href.indexOf("/f/");
        int skip = 3;
        if (marker < 0) {
            marker = href.indexOf("/file/");
            skip = 6;
        }
        if (marker < 0) {
            return "";
        }
        String rest = href.substring(marker + skip);
        int cut = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '?' || c == '#' || c == '/') {
                cut = i;
                break;
            }
        }
        return rest.substring(0, cut);
    }

    private FileInfo fileInfoFrom(JsonObject file) {
        long size = CtfileSession.longValue(file.getValue("file_size_bytes"), 0L);
        FileInfo info = new FileInfo()
                .setFileName(file.getString("file_name", ""))
                .setFileId(CtfileSession.text(file.getValue("file_id")))
                .setSize(size)
                .setSizeStr(file.getString("file_size", ""))
                .setFileType("file")
                .setCreateBy(file.getString("username", ""))
                .setPanType(shareLinkInfo.getType());
        String created = file.getString("file_time", "");
        if (!created.isBlank()) {
            info.setCreateTime(created);
        }
        return info;
    }

    private String turnstileToken() {
        if (shareLinkInfo.getOtherParam() == null) {
            return "";
        }
        Object value = shareLinkInfo.getOtherParam().get("turnstileToken");
        return value == null ? "" : value.toString().trim();
    }

    private int maxListPages() {
        if (shareLinkInfo.getOtherParam() == null || !shareLinkInfo.getOtherParam().containsKey("ctMaxListPages")) {
            return DEFAULT_MAX_LIST_PAGES;
        }
        int configured = CtfileSession.intValue(shareLinkInfo.getOtherParam().get("ctMaxListPages"), DEFAULT_MAX_LIST_PAGES);
        return configured < 1 ? 1 : configured;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current
                && (current.getMessage() == null || current.getMessage().isBlank()
                || current.getClass().getName().contains("CompletionException")
                || current.getClass().getName().contains("ExecutionException"))) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static WorkerExecutor worker() {
        synchronized (WORKER_LOCK) {
            if (workerShutdown) {
                throw new IllegalStateException("城通解析线程池已关闭");
            }
            if (sharedWorker == null) {
                sharedWorker = WebClientVertxInit.get().createSharedWorkerExecutor("ctfile-worker", 4);
            }
            return sharedWorker;
        }
    }

    private static final class ParsedDownload {
        final String url;
        final java.util.Map<String, String> headers;
        final JsonObject file;
        final String fileDir;

        ParsedDownload(String url, java.util.Map<String, String> headers, JsonObject file, String fileDir) {
            this.url = url;
            this.headers = headers;
            this.file = file;
            this.fileDir = fileDir;
        }
    }
}
