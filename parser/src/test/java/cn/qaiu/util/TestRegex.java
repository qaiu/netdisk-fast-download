package cn.qaiu.util;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestRegex {

    @Test
    public void regexYFC() {
        String html = """
                true|
                                                           <div class="load" id="go"><a
                                                             href="https://nsub2d3.118pan.com/dl.php?YzljNGFnOHFabWpsdzVXNUhyOXNiQ0ZYMHEzUjFoQ0R6MExCckdKZmRQL1RXbERPSTUwS0xlZkUvdmN5dGNqTHhCNDJVZURFekthV0tROHNINXdNRkZYUXB5aGQ5TEdXeTgrNkc1TkZwOWhzYVdpak83dEZleWxpYnloc0c1Ky9wbVBSZ0xiSHo5aTBnVVpCRmpyMGFBRERjcldtc0FnbS9vMnd3ZGRHWU4zYTNzNncwbk56UEtOVWhFeGIvWlM1aWZLQjlaTjB3b2V0cGVrb2lpQ0x3U0dtQkJqd2plWk1yeU1mZG1oNXpRYUJpYmFhZVgvWFZScThsTFhmU1ZKRW9UT3haVHFMVGVRTUhR"
                                                             onclick="down_process2('1228264');" target="_blank"><span class="txt">极速下载</span></a><a
                                                             href="https://nsub2t3.118pan.com/dl.php?YzljNGFnOHFabWpsdzVXNUhyOXNiQ0ZYMHEzUjFoQ0R6MExCckdKZmRQL1RXbERPSTUwS0xlZkUvdmN5dGNqTHhCNDJVZURFekthV0tROHNINXdNRkZYUXB5aGQ5TEdXeTgrNkc1TkZwOWhzYVdpak83dEZleWxpYnloc0c1Ky9wbVBSZ0xiSHo5aTBnVVpCRmpyMGFBRERjcldtc0FnbS9vMnd3ZGRHWU4zYTNzNncwbk56UEtOVWhFeGIvWlM1aWZLQjlaTjB3b2V0cGVrb2lpQ0x3U0dtQkJqd2plWk1yeU1mZG1oNXpRYUJpYmFhZVgvWFZScThsTFhmU1ZKRW9UT3haVHFMVGVRTUhR"
                                                             onclick="down_process2('1228264');" target="_blank"><span class="txt txtc">电信下载</span></a><a
                                                             href="https://nsub2u3.118pan.com/dl.php?YzljNGFnOHFabWpsdzVXNUhyOXNiQ0ZYMHEzUjFoQ0R6MExCckdKZmRQL1RXbERPSTUwS0xlZkUvdmN5dGNqTHhCNDJVZURFekthV0tROHNINXdNRkZYUXB5aGQ5TEdXeTgrNkc1TkZwOWhzYVdpak83dEZleWxpYnloc0c1Ky9wbVBSZ0xiSHo5aTBnVVpCRmpyMGFBRERjcldtc0FnbS9vMnd3ZGRHWU4zYTNzNncwbk56UEtOVWhFeGIvWlM1aWZLQjlaTjB3b2V0cGVrb2lpQ0x3U0dtQkJqd2plWk1yeU1mZG1oNXpRYUJpYmFhZVgvWFZScThsTFhmU1ZKRW9UT3haVHFMVGVRTUhR"
                                                             onclick="down_process2('1228264');" target="_blank"><span class="txt">联通下载</span></a><a
                                                             href="https://nsub2d3.118pan.com/dl.php?YzljNGFnOHFabWpsdzVXNUhyOXNiQ0ZYMHEzUjFoQ0R6MExCckdKZmRQL1RXbERPSTUwS0xlZkUvdmN5dGNqTHhCNDJVZURFekthV0tROHNINXdNRkZYUXB5aGQ5TEdXeTgrNkc1TkZwOWhzYVdpak83dEZleWxpYnloc0c1Ky9wbVBSZ0xiSHo5aTBnVVpCRmpyMGFBRERjcldtc0FnbS9vMnd3ZGRHWU4zYTNzNncwbk56UEtOVWhFeGIvWlM1aWZLQjlaTjB3b2V0cGVrb2lpQ0x3U0dtQkJqd2plWk1yeU1mZG1oNXpRYUJpYmFhZVgvWFZScThsTFhmU1ZKRW9UT3haVHFMVGVRTUhR"
                                                             onclick="down_process2('1228264');" target="_blank"><span class="txt txtc">普通下载</span></a></div>
                """;

        Pattern compile = Pattern.compile("href=\"([^\"]+)\"");
        Matcher matcher = compile.matcher(html);
        if (matcher.find()) {
//            System.out.println(matcher.group(0));
            System.out.println(matcher.group(1));
        }
    }
}
