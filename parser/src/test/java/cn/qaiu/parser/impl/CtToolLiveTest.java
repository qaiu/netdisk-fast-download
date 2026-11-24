package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.ParserCreate;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 城通真实联调。默认不跑：
 * mvn -pl parser -DskipTests=false -Dctfile.live=true -Dtest=CtToolLiveTest test
 */
public class CtToolLiveTest {

    private static final String FREE_FILE = "https://474b.com/file/4015376-131945810";
    private static final String PASSWORD_FILE =
            "https://url94.ctfile.com/f/64115194-17569800420720-06c697?p=7609";
    private static final String PASSWORD_FILE_WITHOUT_PASSWORD =
            "https://url94.ctfile.com/f/64115194-17569800420720-06c697";
    private static final String PASSWORD_DIR =
            "https://url94.ctfile.com/d/64115194-164803691-48508c?p=7609&d=164803691&fk=decb36";

    private static Vertx vertx;

    @AfterClass
    public static void tearDown() throws Exception {
        CtfileSession.clearCachedPasses();
        if (vertx != null) {
            CtTool.shutdownWorkerExecutor();
            vertx.close().toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    public void freeFileDownloadsRealPdfBytes() throws Exception {
        liveOnly();
        CtTool tool = tool(FREE_FILE);
        String url = tool.parse().toCompletionStage().toCompletableFuture().get(90, TimeUnit.SECONDS);
        FileInfo info = (FileInfo) tool.getShareLinkInfo().getOtherParam().get("fileInfo");
        assertNotNull(info);
        assertTrue(info.getFileName().endsWith(".pdf"));
        assertTrue(info.getSize() > 1000);
        assertTrue(url.startsWith("https://"));

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<byte[]> head = client.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Range", "bytes=0-15")
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertTrue("head status " + head.statusCode(), head.statusCode() == 206 || head.statusCode() == 200);
        String magic = new String(head.body(), 0, Math.min(5, head.body().length), StandardCharsets.ISO_8859_1);
        assertEquals("%PDF-", magic);
        String range = head.headers().firstValue("content-range").orElse("");
        String length = head.headers().firstValue("content-length").orElse("");
        assertTrue(range.endsWith("/" + info.getSize()) || length.equals(String.valueOf(info.getSize()))
                || range.contains("/" + info.getSize()));

        long size = info.getSize();
        HttpResponse<byte[]> tail = client.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Range", "bytes=" + Math.max(0, size - 20) + "-" + (size - 1))
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        String ending = new String(tail.body(), StandardCharsets.ISO_8859_1);
        assertTrue(ending.contains("%%EOF"));
    }

    @Test
    public void sameSessionListsParentDirectory() throws Exception {
        liveOnly();
        ShareLinkInfo info = ParserCreate.fromShareUrl(FREE_FILE).getShareLinkInfo();
        try (CtfileSession session = CtfileSession.open(info)) {
            JsonObject resource = session.loadResource("file", info.getShareKey(),
                    CtfileSession.ShareTarget.from(info), "", "");
            JsonObject file = CtfileSession.dataOf(resource).getJsonObject("file");
            String fileDir = file.getString("file_dir", "");
            int marker = fileDir.indexOf("/d/");
            assertTrue(fileDir, marker >= 0);
            String dirId = fileDir.substring(marker + 3);
            int query = dirId.indexOf('?');
            if (query >= 0) {
                dirId = dirId.substring(0, query);
            }
            JsonObject listing = session.listing("dir", dirId, new CtfileSession.ShareTarget("0", "", "0"),
                    "", resource, 0, 20);
            JsonArray rows = CtfileSession.rowsOf(listing);
            assertTrue(rows.size() > 0);
            ShareLinkInfo directory = ShareLinkInfo.newBuilder()
                    .type("ctd")
                    .panName("城通网盘-目录")
                    .shareKey(dirId)
                    .shareUrl("https://474b.com/d/" + dirId)
                    .build();
            directory.getOtherParam().put("domainName", "https://nfd.example");
            int named = 0;
            for (int i = 0; i < rows.size(); i++) {
                FileInfo item = CtTool.toFileInfo(rows.getJsonObject(i), directory);
                if (item != null && item.getFileName() != null && !item.getFileName().isBlank()) {
                    named++;
                    assertNotNull(item.getParserUrl());
                }
            }
            assertTrue(named > 0);
        }
    }

    @Test
    public void passwordShareIsTurnstileAndMissingShareIsNotFound() throws Exception {
        liveOnly();
        String withPassword = failure(tool(PASSWORD_FILE).parse());
        assertTrue(withPassword, withPassword.contains("未配置城通账号"));
        assertFalse(withPassword, withPassword.contains("已过期"));
        assertFalse(withPassword, withPassword.contains("密码不正确"));

        String withoutPassword = failure(tool(PASSWORD_FILE_WITHOUT_PASSWORD).parse());
        assertTrue(withoutPassword, withoutPassword.contains("需要访问密码"));

        CtTool directory = (CtTool) ParserCreate.fromShareUrl(PASSWORD_DIR).createTool();
        String directoryFailure = failure(directory.parseFileList());
        assertTrue(directoryFailure, directoryFailure.contains("未配置城通账号"));

        String missingFile = failure(tool("https://474b.com/file/4015376-1").parse());
        assertTrue(missingFile, missingFile.contains("不存在或已过期"));

        String missingOwner = failure(tool("https://474b.com/file/0-0").parse());
        assertTrue(missingOwner, missingOwner.contains("不存在或已过期"));
    }

    private static void liveOnly() {
        Assume.assumeTrue("set -Dctfile.live=true to call ctfile", Boolean.getBoolean("ctfile.live"));
        if (vertx == null) {
            vertx = Vertx.vertx();
            WebClientVertxInit.init(vertx);
        }
    }

    private static CtTool tool(String url) {
        return (CtTool) ParserCreate.fromShareUrl(url).createTool();
    }

    private static String failure(io.vertx.core.Future<?> future) throws Exception {
        try {
            future.toCompletionStage().toCompletableFuture().get(90, TimeUnit.SECONDS);
            return "";
        } catch (Exception e) {
            Throwable current = e;
            StringBuilder message = new StringBuilder();
            while (current != null) {
                if (current.getMessage() != null) {
                    message.append(current.getMessage()).append('\n');
                }
                current = current.getCause();
            }
            if (message.length() == 0) {
                throw e;
            }
            return message.toString();
        }
    }
}
