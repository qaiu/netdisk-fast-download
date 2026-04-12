package cn.qaiu.parser;

import cn.qaiu.entity.ShareLinkInfo;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.regex.Pattern.compile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2024/8/8 2:39
 */
public class PanDomainTemplateTest {

    @Test
    public void normalizeShareLink() {
        // 准备测试数据
        String testShareUrl = "https://test.lanzoux.com/s/someShareKey";

        ParserCreate parserCreate = ParserCreate.fromShareUrl(testShareUrl); // 假设使用LZ网盘模板

        // 调用normalizeShareLink方法
        ShareLinkInfo result = parserCreate.getShareLinkInfo();
        System.out.println(result);
        // 断言结果是否符合预期
        assertNotNull("Result should not be null", result);
        assertEquals("Share key should match", "someShareKey", result.getShareKey());
        assertEquals("Standard URL should be generated correctly", parserCreate.getStandardUrlTemplate().replace("{shareKey}", "someShareKey"), result.getStandardUrl());
        // 可以添加更多的断言来验证其他字段
    }

    @Test
    public void fromShareUrl() throws InterruptedException {
        // 准备测试数据
        String lzUrl = "https://wwn.lanzouy.com/ihLkw1gezutg";
        String cowUrl = "https://cowtransfer.com/s/9a644fe3e3a748";
        String ceUrl = "https://pan.huang1111.cn/s/g31PcQ";
        String wsUrl = "https://f.ws59.cn/f/f25625rv6p6";
//        ParserCreate.fromShareUrl(wsUrl).createTool()
//                .parse().onSuccess(System.out::println);
//        ParserCreate.fromShareUrl(lzUrl).createTool()
//                .parse().onSuccess(System.out::println);
//        ParserCreate.fromShareUrl(cowUrl).createTool()
//                .parse().onSuccess(System.out::println);
        ParserCreate.fromShareUrl(lzUrl).createTool()
                .parse().onSuccess(System.out::println);

//        ParserCreate.fromType("lz").shareKey("ihLkw1gezutg")
//                .createTool().parse().onSuccess(System.out::println);
//        ParserCreate.LZ.shareKey("ihLkw1gezutg")
//                .createTool().parse().onSuccess(System.out::println);


        // 调用fromShareUrl方法
//        PanDomainTemplate resultTemplate = ParserCreate.fromShareUrl(testShareUrl);
//        System.out.println(resultTemplate.normalizeShareLink(testShareUrl));
//        System.out.println(resultTemplate.shareKey("xxx"));
//        System.out.println(resultTemplate.createTool("xxx",null).parse()
//                .onSuccess(System.out::println));
//        System.out.println(resultTemplate.getDisplayName());
//        System.out.println(resultTemplate.getStandardUrlTemplate());
//        System.out.println(resultTemplate.getRegexPattern());
//
//        // 断言结果是否符合预期
//        assertNotNull("Result should not be null", resultTemplate);
//        assertEquals("Should return the correct template", ParserCreate.LZ, resultTemplate);
//        // 可以添加更多的断言来验证正则表达式匹配逻辑
//        new Scanner(System.in).nextLine();
        TimeUnit.SECONDS.sleep(5);
    }


    @Test
    public void testWsPatternMatching() {
        Pattern wsPattern = PanDomainTemplate.WS.getPattern();

        // 历史域名
        String[] positiveUrls = {
                "https://f.ws59.cn/f/f25625rv6p6",
                "https://f.ws28.cn/f/somekey123",
                "https://www.wenshushu.cn/f/abc123",
                // 新增域名
                "https://www.wenxiaozhan.net/f/testkey1",
                "https://www.wenxiaozhan.cn/f/testkey2",
                "https://www.wss.show/f/testkey3",
                "https://www.ws28.cn/f/testkey4",
                "https://www.wss.email/f/testkey5",
                "https://www.wss1.cn/f/testkey6",
                "https://www.ws59.cn/f/testkey7",
                "https://www.wss.cc/f/testkey8",
                "https://www.wss.pet/f/testkey9",
                "https://www.wss.ink/f/testkey10",
                "https://www.wenxiaozhan.com/f/testkey11",
                "https://www.wenshushu.com/f/testkey12",
                "https://www.wss.zone/f/testkey13",
        };

        for (String url : positiveUrls) {
            Matcher m = wsPattern.matcher(url);
            assertTrue("WS pattern should match: " + url, m.matches());
            assertNotNull("KEY group should not be null for: " + url, m.group("KEY"));
        }

        // 验证 KEY 提取正确性
        Matcher m1 = wsPattern.matcher("https://f.ws59.cn/f/f25625rv6p6");
        assertTrue(m1.matches());
        assertEquals("f25625rv6p6", m1.group("KEY"));

        Matcher m2 = wsPattern.matcher("https://www.wenshushu.cn/f/abc123");
        assertTrue(m2.matches());
        assertEquals("abc123", m2.group("KEY"));

        // 负例：错误路径不匹配
        assertFalse("Wrong path should not match",
                wsPattern.matcher("https://www.wenshushu.cn/x/abc123").matches());

        // 负例：非白名单域名不匹配
        assertFalse("Non-whitelisted domain should not match",
                wsPattern.matcher("https://www.evil.com/f/abc123").matches());
    }

    @Test
    public void testLzPatternWebgetstore() {
        Pattern lzPattern = PanDomainTemplate.LZ.getPattern();

        // webgetstore.com 以前遗漏，现已补入
        Matcher m1 = lzPattern.matcher("https://webgetstore.com/somekey");
        assertTrue("LZ should match webgetstore.com", m1.find());
        assertEquals("somekey", m1.group("KEY"));

        Matcher m2 = lzPattern.matcher("https://www.webgetstore.com/somekey");
        assertTrue("LZ should match www.webgetstore.com", m2.find());
        assertEquals("somekey", m2.group("KEY"));

        // t-is.cn 以前遗漏，现已补入
        Matcher m3 = lzPattern.matcher("https://t-is.cn/somekey");
        assertTrue("LZ should match t-is.cn", m3.find());
        assertEquals("somekey", m3.group("KEY"));

        Matcher m4 = lzPattern.matcher("https://www.t-is.cn/somekey");
        assertTrue("LZ should match www.t-is.cn", m4.find());
        assertEquals("somekey", m4.group("KEY"));

        // 已有域名仍然正常匹配
        Matcher m5 = lzPattern.matcher("https://www.lanzoul.com/somekey");
        assertTrue("LZ should match existing domain lanzoul.com", m5.find());
        assertEquals("somekey", m5.group("KEY"));
    }

    @Test
    public void testLePatternFix() {
        Pattern lePattern = PanDomainTemplate.LE.getPattern();

        // lecloud.lenovo.com 应匹配
        Matcher m1 = lePattern.matcher("https://lecloud.lenovo.com/share/abc123");
        assertTrue("LE should match lecloud.lenovo.com", m1.find());
        assertEquals("abc123", m1.group("KEY"));

        // leclou.lenovo.com (去掉'd') 不应匹配（原 lecloud? 的 bug）
        assertFalse("LE should NOT match leclou.lenovo.com",
                lePattern.matcher("https://leclou.lenovo.com/share/abc123").find());
    }

    @Test
    public void testCowPatternFix() {
        Pattern cowPattern = PanDomainTemplate.COW.getPattern();

        // 正常域名
        Matcher m1 = cowPattern.matcher("https://cowtransfer.com/s/abc123");
        assertTrue("COW should match cowtransfer.com", m1.find());
        assertEquals("abc123", m1.group("KEY"));

        Matcher m2 = cowPattern.matcher("https://share.cowtransfer.com/s/abc123");
        assertTrue("COW should match share.cowtransfer.com", m2.find());
        assertEquals("abc123", m2.group("KEY"));

        // 潜在的URL注入：`(.*)` 是贪婪捕获组，可匹配 `evil.com/redirect/` 等前缀，
        // 使形如 `https://evil.com/redirect/cowtransfer.com/s/key` 的 URL 被误识别。
        // 修复后改为 `(?:[a-zA-Z\d-]+\.)?` 仅匹配一级合法子域名（可选），消除误匹配。
        assertFalse("COW should NOT match redirect URLs containing cowtransfer.com in path",
                cowPattern.matcher("https://evil.com/redirect/cowtransfer.com/s/abc").find());
    }

    @Test
    public void testMnePatternFix() {
        Pattern mnePattern = PanDomainTemplate.MNE.getPattern();

        // 带 #/ 前缀的完整网页链接（修复前因 (y.) 未转义而存在 bug）
        Matcher m1 = mnePattern.matcher("https://music.163.com/#/song?id=12345");
        assertTrue("MNE should match #/song format", m1.find());
        assertEquals("12345", m1.group("KEY"));

        // 带 m/ 前缀的移动端链接
        Matcher m2 = mnePattern.matcher("https://music.163.com/m/song?id=12345");
        assertTrue("MNE should match m/song format", m2.find());
        assertEquals("12345", m2.group("KEY"));

        // y.music.163.com 子域名
        Matcher m3 = mnePattern.matcher("https://y.music.163.com/song?id=12345");
        assertTrue("MNE should match y.music.163.com", m3.find());
        assertEquals("12345", m3.group("KEY"));

        // 原 (y.) 中 `.` 未转义（`.` 匹配任意字符）：对于 `yXmusic.163.com`，
        // `(y.)` 会消费 `yX`（y + 任意字符），剩余 `music.163.com` 再被 `music\.163\.com` 匹配，导致误匹配。
        // 修复后 `(y\.)` 要求字面 `.`，`yX` 中 X ≠ `.` 无法匹配，不再误匹配。
        assertFalse("MNE should NOT match yXmusic.163.com (old (y.) could erroneously match via backtracking)",
                mnePattern.matcher("https://yXmusic.163.com/song?id=12345").find());
    }

    @Test
    public void testP115PatternFix() {
        Pattern p115Pattern = PanDomainTemplate.P115.getPattern();

        // 正常匹配
        Matcher m1 = p115Pattern.matcher("https://115.com/s/abc123");
        assertTrue("P115 should match 115.com", m1.find());
        assertEquals("abc123", m1.group("KEY"));

        Matcher m2 = p115Pattern.matcher("https://anxia.com/s/abc123");
        assertTrue("P115 should match anxia.com", m2.find());
        assertEquals("abc123", m2.group("KEY"));

        // 原 .com 未转义时 115Xcom 会被误匹配（现已修复）
        assertFalse("P115 should NOT match 115Xcom",
                p115Pattern.matcher("https://115Xcom/s/abc123").find());
    }

    @Test
    public void testPgdSubdomain() {
        Pattern pgdPattern = PanDomainTemplate.PGD.getPattern();

        // 标准链接
        Matcher m1 = pgdPattern.matcher("https://drive.google.com/file/d/abc123/view?usp=sharing");
        assertTrue("PGD should match standard drive.google.com", m1.find());
        assertEquals("abc123", m1.group("KEY"));

        // 带子域名的链接（修复后支持）
        Matcher m2 = pgdPattern.matcher("https://adsd.drive.google.com/file/d/151bR-nk-tOBm9QAFaozJIVt2WYyCMkoz/view");
        assertTrue("PGD should match subdomain.drive.google.com", m2.find());
        assertEquals("151bR-nk-tOBm9QAFaozJIVt2WYyCMkoz", m2.group("KEY"));
    }

    @Test
    public void verifyDuplicates() {

        // 校验重复
        Set<String> collect =
                Arrays.stream(PanDomainTemplate.values()).map(PanDomainTemplate::getRegex).collect(Collectors.toSet());
        if (collect.size()<PanDomainTemplate.values().length) {
            System.out.println("有重复枚举正则");
        } else {
            System.out.println("正则无重复");
        }

        Set<String> collect2 =
                Arrays.stream(PanDomainTemplate.values()).map(PanDomainTemplate::getStandardUrlTemplate).collect(Collectors.toSet());
        if (collect2.size()<PanDomainTemplate.values().length) {
            System.out.println("有重复枚举标准链接");
        } else {
            System.out.println("标准链接无重复");
        }
    }
}
