package cn.qaiu.vx.core.util;

import io.vertx.core.MultiMap;
import org.apache.commons.beanutils2.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * 参数 工具类
 * <br>Create date 2021-04-30 09:22:18
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public final class ParamUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParamUtil.class);

    public static Map<String, String> multiMapToMap(MultiMap multiMap) {
        if (multiMap == null) return null;
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, String> entry : multiMap.entries()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    public static <T> T multiMapToEntity(MultiMap multiMap, Class<T> tClass) throws NoSuchMethodException {
        Map<String, String> map = multiMapToMap(multiMap);
        T obj = null;
        try {
            obj = tClass.getDeclaredConstructor().newInstance();
            BeanUtils.populate(obj, map);
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            LOGGER.error("实例化异常");
        } catch (InvocationTargetException e2) {
            e2.printStackTrace();
            LOGGER.error("map2bean转换异常");
        }
        return obj;
    }

    public static MultiMap paramsToMap(String paramString) {
        MultiMap entries = MultiMap.caseInsensitiveMultiMap();
        if (paramString == null) return entries;
        String[] params = paramString.split("&");
        if (params.length == 0) return entries;
        for (String param : params) {
            String[] kv = param.split("=");
            if (kv.length == 2) {
                entries.set(kv[0], kv[1]);
            } else {
                entries.set(kv[0], "");
            }
        }
        return entries;
    }

}
