package cn.qaiu.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FCURLParser {// 定义前缀
    public static final String SHARE_URL_PREFIX0 = "https://v2.fangcloud.com/s";
    public static final String SHARE_URL_PREFIX = "https://v2.fangcloud.com/sharing/";
    public static final String SHARE_URL_PREFIX2 = "https://v2.fangcloud.cn/sharing/";

    // 定义正则表达式，适用于所有前缀
    private static final String SHARING_REGEX = "https://www\\.ecpan\\.cn/web(/%23|/#)?/yunpanProxy\\?path=.*&data=" +
            "([^&]+)&isShare=1";

    public static void main(String[] args) {
        // 测试 URL
        String[] urls = {
                "https://www.ecpan.cn/web/#/yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data=4b3d786755688b85c6eb0c04b9124f4dalzdaJpXHx&isShare=1",
                "https://www.ecpan.cn/web/yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data=4b3d786755688b85c6eb0c04b9124f4dalzdaJpXHx&isShare=1",
                "https://v2.fangcloud.cn/sharing/xyz789"
        };

        // 编译正则表达式
        Pattern pattern = Pattern.compile(SHARING_REGEX);

        for (String url : urls) {
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                System.out.println(matcher.groupCount());
                String shareKey = matcher.group(matcher.groupCount()); // 捕捉组 3
                System.out.println("Captured part: " + shareKey);
            } else {
                System.out.println("No match found.");
            }
        }
    }
}
