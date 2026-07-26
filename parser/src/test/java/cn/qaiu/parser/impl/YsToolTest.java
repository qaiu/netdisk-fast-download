package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.parser.PanDomainTemplate;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.util.CommonUtils;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * 永硕E盘解析测试（含示例空间联调）
 */
public class YsToolTest {

    private static Vertx vertx;

    @BeforeClass
    public static void setUpClass() {
        vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
    }

    @AfterClass
    public static void tearDownClass() {
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

        assertFalse(pattern.matcher("https://www.ysepan.com/").matches());
        assertFalse(pattern.matcher("https://c6.ysepan.com/api/ml/mldq").matches());
        assertFalse(pattern.matcher("https://ys-c.ysepan.com/wap/qaiu/x/y/z").matches());
        assertFalse(pattern.matcher("https://zy.ysepan.com/assets/index.js").matches());
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
                "qaiu",
                "UOkGHeA9O9hFJHG",
                "rEBaljD.Ba69AMzTBmAb9AC9CPvC2E",
                "C",
                "Pycharm2023.1激活.zip",
                true);
        assertEquals(
                "https://ys-c.ysepan.com/wap/qaiu/_UOkGHeA9O9hFJHG/rEBaljD.Ba69AMzTBmAb9AC9CPvC2E/Pycharm2023.1%E6%BF%80%E6%B4%BB.zip",
                url);
    }

    @Test
    public void testQaiuSpaceFileListAndDownload() throws Exception {
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
        String decoded = CommonUtils.urlBase64Decode(param);
        JsonObject paramJson = new JsonObject(decoded);

        ParserCreate byId = ParserCreate.fromType("ys").shareKey("qaiu");
        byId.getShareLinkInfo().setSharePassword("qaiuys168");
        byId.getShareLinkInfo().getOtherParam().put("paramJson", paramJson);

        String downloadUrl = byId.createTool().parseById()
                .toCompletionStage().toCompletableFuture()
                .get(60, TimeUnit.SECONDS);

        assertNotNull(downloadUrl);
        assertTrue(downloadUrl.contains("ys-c.ysepan.com") || downloadUrl.contains("ysepan.com"));
        assertTrue(downloadUrl.contains("Pycharm") || downloadUrl.contains("%E6%BF%80%E6%B4%BB"));
        System.out.println("qaiu downloadUrl=" + downloadUrl);
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
        assertTrue("应包含文件或URL条目",
                files.stream().anyMatch(f -> "file".equals(f.getFileType()) || "url".equals(f.getFileType())));
        System.out.println("sohehe4 dir=" + dirId + " entries=" + files.size());
    }
}
