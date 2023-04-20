package cn.qaiu.vx.core.util;

import java.util.HashMap;
import java.util.Map;

/**
 * vertx 上下文外的本地容器 为不在vertx线程的方法传递数据
 * <br>Create date 2021-05-07 10:26:54
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class LocalConstant {
    private static final Map<String, Object> LOCAL_CONST = new HashMap<>();

    public static Map<String, Object> put(String k, Object v) {
        if (LOCAL_CONST.containsKey(k)) return LOCAL_CONST;
        LOCAL_CONST.put(k, v);
        return LOCAL_CONST;
    }

    public static Object get(String k) {
        return LOCAL_CONST.get(k);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getWithCast(String k) {
        return (T) LOCAL_CONST.get(k);
    }

    public static boolean containsKey(String k) {
        return LOCAL_CONST.containsKey(k);
    }

    public static Map<?, ?> getMap(String k) {
        return (Map<?, ?>) LOCAL_CONST.get(k);
    }

    public static String getString(String k) {
        return LOCAL_CONST.get(k).toString();
    }


}
