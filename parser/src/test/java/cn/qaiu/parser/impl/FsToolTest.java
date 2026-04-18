package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanDomainTemplate;
import cn.qaiu.parser.ParserCreate;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * 飞书云盘解析测试
 */
public class FsToolTest {

    @Test
    public void testFsPatternMatchFile() {
        Pattern fsPattern = PanDomainTemplate.FS.getPattern();

        // 文件链接 (带 ?from=from_copylink)
        Matcher m1 = fsPattern.matcher(
                "https://kcncuknojm60.feishu.cn/file/VnCxbt35KoowKoxldO3c3C7VnMc?from=from_copylink");
        assertTrue("FS pattern should match file URL with query params", m1.matches());
        assertEquals("VnCxbt35KoowKoxldO3c3C7VnMc", m1.group("KEY"));

        // 文件链接 (不带查询参数)
        Matcher m2 = fsPattern.matcher(
                "https://kcncuknojm60.feishu.cn/file/VnCxbt35KoowKoxldO3c3C7VnMc");
        assertTrue("FS pattern should match file URL without query params", m2.matches());
        assertEquals("VnCxbt35KoowKoxldO3c3C7VnMc", m2.group("KEY"));
    }

    @Test
    public void testFsPatternMatchFolder() {
        Pattern fsPattern = PanDomainTemplate.FS.getPattern();

        // 文件夹链接 (带 ?from=from_copylink)
        Matcher m1 = fsPattern.matcher(
                "https://kcncuknojm60.feishu.cn/drive/folder/RQSKf8EQ4l7dMedqzHucpMbancg?from=from_copylink");
        assertTrue("FS pattern should match folder URL with query params", m1.matches());
        assertEquals("RQSKf8EQ4l7dMedqzHucpMbancg", m1.group("KEY"));

        // 文件夹链接 (不带查询参数)
        Matcher m2 = fsPattern.matcher(
                "https://kcncuknojm60.feishu.cn/drive/folder/RQSKf8EQ4l7dMedqzHucpMbancg");
        assertTrue("FS pattern should match folder URL without query params", m2.matches());
        assertEquals("RQSKf8EQ4l7dMedqzHucpMbancg", m2.group("KEY"));
    }

    @Test
    public void testFsPatternDifferentSubdomains() {
        Pattern fsPattern = PanDomainTemplate.FS.getPattern();

        // 不同的租户子域名
        String[] subdomains = {"abc123", "kcncuknojm60", "tenant01", "xyz"};
        for (String subdomain : subdomains) {
            String url = "https://" + subdomain + ".feishu.cn/file/TestToken123";
            Matcher m = fsPattern.matcher(url);
            assertTrue("FS pattern should match subdomain: " + subdomain, m.matches());
            assertEquals("TestToken123", m.group("KEY"));
        }
    }

    @Test
    public void testFsPatternNegativeCases() {
        Pattern fsPattern = PanDomainTemplate.FS.getPattern();

        // 不匹配: 非feishu.cn域名
        assertFalse("Should not match non-feishu domain",
                fsPattern.matcher("https://evil.com/file/TOKEN").matches());

        // 不匹配: 其他路径
        assertFalse("Should not match other paths",
                fsPattern.matcher("https://abc.feishu.cn/docs/TOKEN").matches());

        // 不匹配: 没有子域名的feishu.cn
        assertFalse("Should not match feishu.cn without subdomain",
                fsPattern.matcher("https://feishu.cn/file/TOKEN").matches());
    }

    @Test
    public void testFromShareUrlFile() {
        String fileUrl = "https://kcncuknojm60.feishu.cn/file/VnCxbt35KoowKoxldO3c3C7VnMc?from=from_copylink";

        ParserCreate parserCreate = ParserCreate.fromShareUrl(fileUrl);
        ShareLinkInfo info = parserCreate.getShareLinkInfo();

        assertNotNull("ShareLinkInfo should not be null", info);
        assertEquals("Type should be fs", "fs", info.getType());
        assertEquals("Pan name should be 飞书云盘", "飞书云盘", info.getPanName());
        assertEquals("Share key should be the token",
                "VnCxbt35KoowKoxldO3c3C7VnMc", info.getShareKey());
    }

    @Test
    public void testFromShareUrlFolder() {
        String folderUrl = "https://kcncuknojm60.feishu.cn/drive/folder/RQSKf8EQ4l7dMedqzHucpMbancg?from=from_copylink";

        ParserCreate parserCreate = ParserCreate.fromShareUrl(folderUrl);
        ShareLinkInfo info = parserCreate.getShareLinkInfo();

        assertNotNull("ShareLinkInfo should not be null", info);
        assertEquals("Type should be fs", "fs", info.getType());
        assertEquals("Pan name should be 飞书云盘", "飞书云盘", info.getPanName());
        assertEquals("Share key should be the token",
                "RQSKf8EQ4l7dMedqzHucpMbancg", info.getShareKey());
    }

    @Test
    public void testFromType() {
        ParserCreate parserCreate = ParserCreate.fromType("fs")
                .shareKey("VnCxbt35KoowKoxldO3c3C7VnMc");

        ShareLinkInfo info = parserCreate.getShareLinkInfo();

        assertNotNull("ShareLinkInfo should not be null", info);
        assertEquals("Type should be fs", "fs", info.getType());
        assertEquals("Pan name should be 飞书云盘", "飞书云盘", info.getPanName());
        assertEquals("Share key should match", "VnCxbt35KoowKoxldO3c3C7VnMc", info.getShareKey());
    }

    @Test
    public void testFromShareUrlFileWithoutQueryParams() {
        String fileUrl = "https://kcncuknojm60.feishu.cn/file/VnCxbt35KoowKoxldO3c3C7VnMc";

        ParserCreate parserCreate = ParserCreate.fromShareUrl(fileUrl);
        ShareLinkInfo info = parserCreate.getShareLinkInfo();

        assertNotNull("ShareLinkInfo should not be null", info);
        assertEquals("fs", info.getType());
        assertEquals("VnCxbt35KoowKoxldO3c3C7VnMc", info.getShareKey());
    }
}
