package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.parser.ParserCreate;
import io.vertx.core.Vertx;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 蓝奏目录真实联调。默认不跑：
 * mvn -pl parser -DskipTests=false -Dlz.live=true -Dtest=LzFolderLiveTest test
 */
public class LzFolderLiveTest {

    private static Vertx vertx;

    @AfterClass
    public static void close() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    public void oldFolderListsOnBothDomainsAndOneFileDownloads() throws Exception {
        liveOnly();
        List<FileInfo> oui = list("https://pan.lanzoui.com/b66477");
        List<FileInfo> oug = list("https://pan.lanzoug.com/b66477");
        assertFalse(oui.isEmpty());
        assertFalse(oug.isEmpty());
        assertEquals(oui.get(0).getFileName(), oug.get(0).getFileName());
        FileInfo small = smallest(oui);
        String url = ParserCreate.fromShareUrl("https://pan.lanzoui.com/" + small.getFileId())
                .createTool().parse().toCompletionStage().toCompletableFuture().get(90, TimeUnit.SECONDS);
        HttpResponse<byte[]> response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build()
                .send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().length > 0);
        if (small.getSize() != null && small.getSize() > 0 && small.getSize() < 8_000_000) {
            assertEquals(small.getSize().longValue(), response.body().length);
        }
    }

    @Test
    public void passwordFolderStillLists() throws Exception {
        liveOnly();
        List<FileInfo> files = ParserCreate.fromShareUrl("https://moteam.lanzoum.com/b00yaejvte")
                .setShareLinkInfoPwd("1234")
                .createTool().parseFileList()
                .toCompletionStage().toCompletableFuture().get(90, TimeUnit.SECONDS);
        assertFalse(files.isEmpty());
        assertTrue(files.stream().anyMatch(file -> file.getFileName() != null && !file.getFileName().isBlank()));
    }

    private static List<FileInfo> list(String url) throws Exception {
        return ParserCreate.fromShareUrl(url).createTool().parseFileList()
                .toCompletionStage().toCompletableFuture().get(90, TimeUnit.SECONDS);
    }

    private static FileInfo smallest(List<FileInfo> files) {
        FileInfo best = files.get(0);
        for (FileInfo file : files) {
            if (file.getSize() != null && (best.getSize() == null || file.getSize() < best.getSize())) {
                best = file;
            }
        }
        return best;
    }

    private static void liveOnly() {
        Assume.assumeTrue("set -Dlz.live=true to call lanzou", Boolean.getBoolean("lz.live"));
        if (vertx == null) {
            vertx = Vertx.vertx();
            WebClientVertxInit.init(vertx);
        }
    }
}
