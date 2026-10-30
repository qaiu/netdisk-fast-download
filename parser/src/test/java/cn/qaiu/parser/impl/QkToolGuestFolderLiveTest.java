package cn.qaiu.parser.impl;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.Vertx;
import org.junit.Assume;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 对 Issue #282 的分享做一次真实访客解析。
 * 默认不跑：父 POM surefire skipTests=true，且本测试还要求 -Dquark.live=true。
 * mvn -pl parser -DskipTests=false -Dquark.live=true -Dtest=QkToolGuestFolderLiveTest test
 */
public class QkToolGuestFolderLiveTest {

    @Test
    public void issue282FolderShareResolvesAnonymously() throws Exception {
        Assume.assumeTrue("set -Dquark.live=true to call Quark", Boolean.getBoolean("quark.live"));
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        QkTool tool = null;
        try {
            ShareLinkInfo info = ShareLinkInfo.newBuilder()
                    .type("qk")
                    .panName("夸克网盘")
                    .shareKey("d34c18e9d62a")
                    .shareUrl("https://pan.quark.cn/s/d34c18e9d62a")
                    .build();
            tool = new QkTool(info);
            String url = tool.parse().toCompletionStage().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertNotNull(url);
            assertTrue(url.startsWith("http"));
            assertTrue("guest download host, got " + url, url.contains("dl-guest"));
            FileInfo fileInfo = (FileInfo) info.getOtherParam().get("fileInfo");
            assertNotNull(fileInfo);
            assertNotNull(fileInfo.getFileName());
            assertTrue("expected a small file, got " + fileInfo.getSize() + " " + fileInfo.getFileName(),
                    fileInfo.getSize() > 0 && fileInfo.getSize() < 40L * 1024 * 1024);
        } finally {
            if (tool != null) {
                tool.close();
            }
            vertx.close().toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
        }
    }
}
