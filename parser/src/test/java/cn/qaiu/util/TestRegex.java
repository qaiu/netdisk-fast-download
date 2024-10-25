package cn.qaiu.util;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestRegex {

    @Test
    public void regexYFC() {
        String html = """
                https://www.kugou.com/mixsong/9q98o5b9.html
                """;

        Pattern compile = Pattern.compile("https://(.+)\\.kugou\\.com/mixsong/(?<KEY>.+).html");
        Matcher matcher = compile.matcher(html);
        if (matcher.find()) {
            System.out.println(matcher.group(0));
            System.out.println(matcher.group(1));
        }
    }
}
