package cn.qaiu.util;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2023/7/16 1:53
 */
public class PanExceptionUtils {

    public static RuntimeException fillRunTimeException(String name, String dataKey, Throwable t) {
        return new RuntimeException(name + ": 请求异常: key = " + dataKey, t.fillInStackTrace());
    }
}
