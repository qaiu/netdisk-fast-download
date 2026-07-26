package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.parser.PanDomainTemplate;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.util.CommonUtils;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * 永硕E盘解析测试（含示例空间联调 + 真实下载校验）
 */
public class YsToolTest {

    private static Vertx vertx;
    private static WebClient webClient;

    @BeforeClass
    public static void setUpClass() {
        vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        webClient = WebClient.create(vertx);
    }

    @AfterClass
    public static void tearDownClass() {
        if (webClient != null) {
            webClient.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    public void testPatternMatching() {
        Pattern pattern = PanDomainTemplate.YS.getPattern();

        Matcher m1 = pattern.matcher("https://qaiu.ysepan.com/");
        assertTrue(m1.matches());
        assertEquals("qaiu", m1.group("KEY"));

        Matcher m2 = pattern.matcher("http://sohehe4.ysepan.com");
        assertTrue(m2.matches());
        assertEquals("sohehe4", m2.group("KEY"));

        Matcher m3 = pattern.matcher("https://demo.ys168.com/");
        assertTrue(m3.matches());
        assertEquals("demo", m3.group("KEY"));

        assertTrue(pattern.matcher("https://a.cccpan.com/").matches());
        assertTrue(pattern.matcher("https://a.ysupan.com").matches());
        assertTrue(pattern.matcher("https://a.uupan.net/").matches());
        assertTrue(pattern.matcher("https://a.ysok.net").matches());

        assertFalse(pattern.matcher("https://www.ysepan.com/").matches());
        assertFalse(pattern.matcher("https://c6.ysepan.com/api/ml/mldq").matches());
        assertFalse(pattern.matcher("https://ys-c.ysepan.com/wap/qaiu/x/y/z").matches());
        assertFalse(pattern.matcher("https://zy.ysepan.com/assets/index.js").matches());
        assertFalse(pattern.matcher("https://qaiu.evil.com/").matches());
    }

    @Test
    public void testIdentifyShareUrl() {
        ParserCreate create = ParserCreate.fromShareUrl("https://qaiu.ysepan.com/");
        assertEquals("ys", create.getShareLinkInfo().getType());
        assertEquals("永硕E盘", create.getShareLinkInfo().getPanName());
        assertEquals("qaiu", create.getShareLinkInfo().getShareKey());
    }

    @Test
    public void testBuildDownloadUrl() {
        String url = YsTool.buildDownloadUrl(
                "yssl",
                "A95UIe495EkSKE",
                "Bc8hF8Nsbl2I4Ec6vAm5EGe9HU36iC",
                "L",
                "lu20.jpg");
        // 与官方一致：xzpz 前不加 "_"
        assertEquals(
                "https://ys-l.ysepan.com/wap/yssl/A95UIe495EkSKE/Bc8hF8Nsbl2I4Ec6vAm5EGe9HU36iC/lu20.jpg",
                url);
        assertFalse("force-download 前缀会导致 404", url.contains("/_"));
    }

    @Test
    public void testOfficialSampleUrlRealDownload() throws Exception {
        String official = "https://ys-l.ysepan.com/wap/yssl/A95UIe495EkSKE/Bc8hF8Nsbl2I4Ec6vAm5EGe9HU36iC/lu20.jpg";
        String withForcePrefix = "https://ys-l.ysepan.com/wap/yssl/_A95UIe495EkSKE/Bc8hF8Nsbl2I4Ec6vAm5EGe9HU36iC/lu20.jpg";

        Buffer ok = download(official, "https://yssl.ysepan.com/");
        assertTrue("官方直链应能下载到 JPEG", ok.length() > 1000);
        assertEquals((byte) 0xFF, ok.getByte(0));
        assertEquals((byte) 0xD8, ok.getByte(1));

        int forceStatus = webClient.getAbs(withForcePrefix)
                .putHeader("User-Agent", "Mozilla/5.0")
                .putHeader("Referer", "https://yssl.ysepan.com/")
                .send()
                .toCompletionStage().toCompletableFuture()
                .get(30, TimeUnit.SECONDS)
                .statusCode();
        assertNotEquals("带 _ 前缀的直链应失败(文件不存在)", 200, forceStatus);
    }

    @Test
    public void testQaiuSpaceFileListAndRealDownload() throws Exception {
        ParserCreate create = ParserCreate.fromShareUrl("https://qaiu.ysepan.com/");
        create.getShareLinkInfo().setSharePassword("qaiuys168");
        create.getShareLinkInfo().getOtherParam().put("domainName", "http://localhost");

        List<FileInfo> dirs = create.createTool().parseFileList()
                .toCompletionStage().toCompletableFuture()
                .get(60, TimeUnit.SECONDS);

        assertNotNull(dirs);
        assertFalse("目录列表不应为空", dirs.isEmpty());
        assertEquals("folder", dirs.get(0).getFileType());
        String dirId = dirs.get(0).getFileId();
        assertNotNull(dirId);

        ParserCreate filesCreate = ParserCreate.fromShareUrl("https://qaiu.ysepan.com/");
        filesCreate.getShareLinkInfo().setSharePassword("qaiuys168");
        filesCreate.getShareLinkInfo().getOtherParam().put("domainName", "http://localhost");
        filesCreate.getShareLinkInfo().getOtherParam().put("dirId", dirId);

        List<FileInfo> files = filesCreate.createTool().parseFileList()
                .toCompletionStage().toCompletableFuture()
                .get(60, TimeUnit.SECONDS);

        assertNotNull(files);
        FileInfo zip = files.stream()
                .filter(f -> "file".equals(f.getFileType()))
                .filter(f -> f.getFileName() != null && f.getFileName().contains("Pycharm"))
                .findFirst()
                .orElse(null);
        assertNotNull("应能列出 Pycharm 压缩包", zip);
        assertTrue(zip.getSize() > 0);
        assertNotNull(zip.getParserUrl());

        String param = zip.getParserUrl().substring(zip.getParserUrl().lastIndexOf('/') + 1);
        JsonObject paramJson = new JsonObject(CommonUtils.urlBase64Decode(param));
        assertFalse("downloadUrl 不应含 force 前缀",
                paramJson.getString("downloadUrl", "").contains("/_"));

        ParserCreate byId = ParserCreate.fromType("ys").shareKey("qaiu");
        byId.getShareLinkInfo().setSharePassword("qaiuys168");
        byId.getShareLinkInfo().getOtherParam().put("paramJson", paramJson);

        String downloadUrl = byId.createTool().parseById()
                .toCompletionStage().toCompletableFuture()
                .get(60, TimeUnit.SECONDS);

        assertNotNull(downloadUrl);
        assertFalse("解析直链不应含 _xzpz 前缀", downloadUrl.matches(".*/_[^/]+/.*"));
        assertTrue(downloadUrl.contains("ysepan.com"));

        Buffer body = download(downloadUrl, "https://qaiu.ysepan.com/");
        assertEquals("真实下载大小应与列表一致", zip.getSize().longValue(), body.length());
        // ZIP magic: PK
        assertEquals('P', (char) body.getByte(0));
        assertEquals('K', (char) body.getByte(1));
        System.out.println("qaiu real download ok, url=" + downloadUrl + ", size=" + body.length());
    }

    @Test
    public void testFufu1ZmlHierarchyAndUrlItems() throws Exception {
        // https://fufu1.ysepan.com/ 无密码；游戏3 下应按 zml 展示子目录，再进子目录才是夸克/百度链接
        ParserCreate create = ParserCreate.fromShareUrl("https://fufu1.ysepan.com/");
        create.getShareLinkInfo().getOtherParam().put("domainName", "http://localhost");

        List<FileInfo> roots = create.createTool().parseFileList()
                .toCompletionStage().toCompletableFuture()
                .get(60, TimeUnit.SECONDS);
        assertNotNull(roots);
        FileInfo game3 = roots.stream()
                .filter(f -> "folder".equals(f.getFileType()))
                .filter(f -> "游戏3".equals(f.getFileName()))
                .findFirst()
                .orElse(null);
        assertNotNull("应有目录 游戏3", game3);

        ParserCreate level2 = ParserCreate.fromShareUrl("https://fufu1.ysepan.com/");
        level2.getShareLinkInfo().getOtherParam().put("domainName", "http://localhost");
        level2.getShareLinkInfo().getOtherParam().put("dirId", game3.getFileId());

        List<FileInfo> subdirs = level2.createTool().parseFileList()
                .toCompletionStage().toCompletableFuture()
                .get(60, TimeUnit.SECONDS);
        assertNotNull(subdirs);
        assertTrue("游戏3 下应是子目录列表", subdirs.size() > 10);
        assertTrue("游戏3 下列表应全是 folder（zml 子目录）",
                subdirs.stream().allMatch(f -> "folder".equals(f.getFileType())));

        FileInfo sample = subdirs.stream()
                .filter(f -> f.getFileName() != null && f.getFileName().contains("我是未来"))
                .findFirst()
                .orElse(subdirs.get(0));

        // 从 parserUrl 提取 zml，或直接用 fileName
        ParserCreate level3 = ParserCreate.fromShareUrl("https://fufu1.ysepan.com/");
        level3.getShareLinkInfo().getOtherParam().put("domainName", "http://localhost");
        level3.getShareLinkInfo().getOtherParam().put("dirId", game3.getFileId());
        level3.getShareLinkInfo().getOtherParam().put("zml", sample.getFileName());

        List<FileInfo> links = level3.createTool().parseFileList()
                .toCompletionStage().toCompletableFuture()
                .get(60, TimeUnit.SECONDS);
        assertNotNull(links);
        assertFalse(links.isEmpty());
        assertTrue("子目录内应有 url 类型",
                links.stream().anyMatch(f -> "url".equals(f.getFileType())));
        assertTrue("应包含夸克/百度链接名",
                links.stream().anyMatch(f -> "夸克".equals(f.getFileName()) || "百度".equals(f.getFileName())));
        assertFalse("空占位 URL 不应出现",
                links.stream().anyMatch(f -> f.getFileName() == null || f.getFileName().isBlank()));
        assertTrue("URL 条目应带 previewUrl",
                links.stream().filter(f -> "url".equals(f.getFileType()))
                        .allMatch(f -> f.getPreviewUrl() != null && f.getPreviewUrl().startsWith("http")));

        System.out.println("fufu1 hierarchy ok: 游戏3 -> " + sample.getFileName()
                + " -> " + links.stream().map(FileInfo::getFileName).toList());
    }

    @Test
    public void testSohehe4SpaceDirectories() throws Exception {
        ParserCreate create = ParserCreate.fromShareUrl("https://sohehe4.ysepan.com/");
        create.getShareLinkInfo().setSharePassword("1234");
        create.getShareLinkInfo().getOtherParam().put("domainName", "http://localhost");

        List<FileInfo> dirs = create.createTool().parseFileList()
                .toCompletionStage().toCompletableFuture()
                .get(60, TimeUnit.SECONDS);

        assertNotNull(dirs);
        assertTrue("sohehe4 应有多个目录", dirs.size() >= 3);
        assertTrue(dirs.stream().allMatch(d -> "folder".equals(d.getFileType())));

        String dirId = dirs.stream()
                .filter(d -> d.getFileName() != null && d.getFileName().contains("留言"))
                .map(FileInfo::getFileId)
                .findFirst()
                .orElse(dirs.get(0).getFileId());

        ParserCreate filesCreate = ParserCreate.fromShareUrl("https://sohehe4.ysepan.com/");
        filesCreate.getShareLinkInfo().setSharePassword("1234");
        filesCreate.getShareLinkInfo().getOtherParam().put("domainName", "http://localhost");
        filesCreate.getShareLinkInfo().getOtherParam().put("dirId", dirId);

        List<FileInfo> files = filesCreate.createTool().parseFileList()
                .toCompletionStage().toCompletableFuture()
                .get(60, TimeUnit.SECONDS);

        assertNotNull(files);
        assertFalse(files.isEmpty());

        FileInfo file = files.stream()
                .filter(f -> "file".equals(f.getFileType()))
                .filter(f -> f.getSize() != null && f.getSize() > 0 && f.getSize() < 5_000_000)
                .findFirst()
                .orElse(null);
        if (file != null) {
            String param = file.getParserUrl().substring(file.getParserUrl().lastIndexOf('/') + 1);
            JsonObject paramJson = new JsonObject(CommonUtils.urlBase64Decode(param));
            String downloadUrl = paramJson.getString("downloadUrl");
            assertNotNull(downloadUrl);
            assertFalse(downloadUrl.contains("/_"));

            Buffer body = download(downloadUrl, "https://sohehe4.ysepan.com/");
            assertEquals(file.getSize().longValue(), body.length());
            System.out.println("sohehe4 real download ok, file=" + file.getFileName()
                    + ", size=" + body.length());
        } else {
            System.out.println("sohehe4 dir=" + dirId + " entries=" + files.size()
                    + " (no small file for real download sample)");
        }
    }

    private static Buffer download(String url, String referer) throws Exception {
        return webClient.getAbs(url)
                .putHeader("User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36")
                .putHeader("Referer", referer)
                .send()
                .toCompletionStage().toCompletableFuture()
                .thenApply(res -> {
                    assertEquals("下载 HTTP 状态码应为 200: " + url, 200, res.statusCode());
                    Buffer body = res.body();
                    assertNotNull(body);
                    assertTrue("下载内容为空: " + url, body.length() > 0);
                    String ct = res.getHeader("Content-Type");
                    assertFalse("不应返回 HTML 错误页: " + url,
                            ct != null && ct.toLowerCase().contains("text/html"));
                    return body;
                })
                .get(60, TimeUnit.SECONDS);
    }
}
