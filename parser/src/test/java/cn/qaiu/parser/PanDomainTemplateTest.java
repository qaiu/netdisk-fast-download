package cn.qaiu.parser;

import cn.qaiu.entity.ShareLinkInfo;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static java.util.regex.Pattern.compile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
    public void verifyDuplicates() {

        Matcher matcher = compile("https://(?:[a-zA-Z\\d-]+\\.)?drive\\.google\\.com/file/d/(?<KEY>.+)/view(\\?usp=(sharing|drive_link))?")
                .matcher("https://adsd.drive.google.com/file/d/151bR-nk-tOBm9QAFaozJIVt2WYyCMkoz/view");
        if (matcher.find()) {
            System.out.println(matcher.group());
            System.out.println(matcher.group("KEY"));
        }
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
