package qaiu.web.test;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestRegex {

    @Test
    public void regexYFC() {
        String html = """
                    <input type="hidden" id="typed_id" value="file_559003251828">
                    <input type="hidden" id="share_link_token" value="9cbe4b73521ba4d65a8cd38a8c">
                """;

        Pattern compile = Pattern.compile("id=\"typed_id\"\\s+value=\"file_(\\d+)\"");
        Matcher matcher = compile.matcher(html);
        if (matcher.find()) {
            System.out.println(matcher.group(0));
            System.out.println(matcher.group(1));
        }
    }
}
