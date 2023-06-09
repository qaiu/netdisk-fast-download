package cn.qaiu.lz.common.util;

public class CommonUtils {

    public static String parseURL(String urlPrefix, String url) {
        if (!url.startsWith(urlPrefix)) {
            url = urlPrefix + url;
        }
        return url.substring(urlPrefix.length());
    }
}
