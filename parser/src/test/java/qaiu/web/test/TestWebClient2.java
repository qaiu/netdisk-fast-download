package qaiu.web.test;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestWebClient2 {

    @Test
    public void matcherHtml() {

        Pattern compile = Pattern.compile("class=\"ifr2\" name=.+src=\"(/fn\\?[a-zA-Z0-9_+/=]{16,})\"");
        var text = """
<div class="ifr"><!--<iframe class="ifr2" name="1" src="/fn?v2" frameborder="0" scrolling="no"></iframe>-->
<iframe class="ifr2" name="1685001208" src="/fn?UzUBa1oxBmUAYgNsUDUFNVI6BjJfJlchV21TZFU_aVWwANVQzXTBXMlUxUTcLZ1dwUn8DYwQ5AHFVOwdmBjRUPlM2AS9aOgY3AGIDMFA2" frameborder="0" scrolling="no"></iframe>""";
        System.out.println(text);
        Matcher matcher = compile.matcher(text);
        if (matcher.find()) {
            System.out.println(matcher.group(0));
            System.out.println(matcher.group(1));
        }
    }

    @Test
    public void lzClient() {
        Vertx vertx = Vertx.vertx();
        WebClient client = WebClient.create(vertx);
        MultiMap form = MultiMap.caseInsensitiveMultiMap();


        // action=downprocess&sign=AGYHOQk4BjdTWgQ7BzcGOlU_bATVSNQMxBDFQZgZoBj4HMFEgWnMOZ1I1A2NWOgUxB20HMlM_aUGoLOgQz&p=e4k4
        form.set("action", "downprocess");
        form.set("sign", "VzFWaA4_aBzYIAQI9ADBUaARvATVRNlNhUGUBNwBuATkDNFEgXHVVPAZhB2dTP1ZiVD5UYQBpV2EBPwA3");
        form.set("p", "e4k4");
        client.postAbs("https://wwsd.lanzoue.com/ajaxm.php")
                .putHeader("referer", "https://wwsd.lanzoue.com/iFhd00x8k0kh")
                .sendForm(form).onSuccess(res -> {
            JsonObject jsonObject = res.bodyAsJsonObject();
            System.out.println(jsonObject);

            vertx.close();
        });

        //
        // https://developer.lanzoug.com/file/?VTMBPwEwU2IGD1dvV2ICblBvU2sENQZhVSZSMAA1WihSOVYsDTZTZlN2UXMFfAZjBzJXJQAxAG5XP1UyXGQGKlVlAXgBbVMpBmNXLFdhAmpQZFN4BCEGbVUiUnIAOloyUj5WZA0PU25TYVE6BWAGNgdlV2IAbQAyV2JValw3BiFVMwElAWFTNgZmVzBXMwIyUDpTYARrBiJVIlIkAGFaaVJiVjMNY1MoUzVRMgV+BjUHaFd9ADwAMVdlVTFcOAYyVWcBYgFqUz4GaVdlVzMCNFBrUzcEOAZgVWJSZQA/WmJSM1Y2DWhTNFMzUTEFYgY3B2VXZgBxAHhXOVUjXCYGclUmATMBLlNuBjRXPFcyAjNQP1NvBG8GPVVqUnIAKFoyUj9WZA02UzpTNFExBWkGMgdtV2IAaQAxV2FVYFwuBilVcwEwATBTcAZtVzBXNQI7UD9TZgRrBjFVY1JlAGlafVInVnENJ1M6UzRRMQVpBjIHbVdiAG0AMldgVWRcJgZyVTwBJgFhUzYGYVczVy0CMVA5U2QEdQY1VWZSYgByWmxSag==
        // https://developer.lanzoug.com/file/?B2FWaA4/BDVTWgc/UWRVOQQ7BT1VZFYxUSJUNgE0UiAEbwJ4CDMOOwInU3EKc1w5ATRSIAIzUz1ROVcwATkDLwc3Vi8OYgR+UzYHfFFnVT0EMAUuVXBWPVEmVHQBO1I6BGgCMAgKDjMCMFM4Cm9cbAFjUmcCb1NhUWRXaAFqAyQHYVZyDm4EYVMzB2BRNVVlBG4FNlU6VnJRJlQiAWBSYQQ0AmcIZg51AmRTMApxXG8BblJ4Aj5TYlFjVzMBZQM3BzVWNQ5lBGlTPAc1UTVVYwQ/BWFVaVYwUWZUYwE+UmoEZQJiCG0OaQJiUzMKbVxtAWNSYwJzUytRP1chAXsDdwd0VmQOIQQ5U2EHbFE0VWQEawU5VT5WbVFuVHQBKVI6BGkCMAgzDmcCZVMzCmZcaAFrUmcCaFNlUWNXZwFzAywHIVZnDj8EJ1M4B2BRM1VsBGsFMFU6VmZRb1RgAW5SdQRxAiUIIg5nAmVTMwpmXGgBa1JnAm9TYVFmV2YBewN3B25WcQ5uBGFTNAdjUStVZgRtBTJVJFZlUWJUZAFzUmQEPA==
        // https://developer.lanzoug.com/file/?CW9WaAk4BzZUXVRsCz5cMAE+Bj5UZVM0USJUNlRhA3FUPwJ4CTJUYQInASMHflI3ATQGdFdmAW9ROQFmVGwEKAk5Vi8JZQd9VDFULws9XDQBNQYtVHFTOFEmVHRUbgNrVDgCMAkLVGkCMAFqB2JSYgFjBjNXOgEzUWQBPlQ/BCMJb1ZyCWkHYlQ0VDMLb1xsAWsGNVQ7U3dRJlQiVDUDMFRkAmcJZ1QvAmQBYgd8UmEBbgYsV2sBMFFjAWVUMAQwCTtWNQliB2pUO1RmC29cagE6BmJUaFM1UWZUY1RrAztUNQJiCWxUMwJiAWEHYFJjAWMGN1cmAXlRPwF3VC4EcAl6VmQJJgc6VGZUPwtuXG0BbgY6VD9TaFFuVHRUfANrVDkCMAkyVD0CZQFhB2tSZgFrBjNXPgE6UWABM1QmBCsJL1ZnCTgHJFQ/VDMLaVxlAW4GM1Q7U2RRb1RmVDgDJFQhAiUJI1Q9AmUBYQdrUmYBawYzVzoBM1FmATBULgRwCWBWcQlpB2JUM1QwC3FcbwFoBjFUJVNgUWJUZFQmAzVUbA==
    }
}
