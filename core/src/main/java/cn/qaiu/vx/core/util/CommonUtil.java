package cn.qaiu.vx.core.util;

import cn.qaiu.vx.core.annotaions.HandleSortFilter;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CommonUtil
 * <br>Create date 2021/5/8 14:52
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class CommonUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonUtil.class);

    /**
     * 匹配正则list
     *
     * @param regList 正则list
     * @param destStr 要匹配的目标字符
     * @return 匹配成功返回true 否则返回false
     */
    public static boolean matchRegList(List<?> regList, String destStr) {
        // 判断是否忽略
        for (Object ignores : regList) {
            if (destStr.matches(ignores.toString())) {
                return true;
            }
        }
        return false;
    }


    /**
     * 测试本机端口是否被使用
     *
     * @param port port
     * @return boolean
     */
    public static boolean isPortUsing(int port) {
        //如果该端口还在使用则返回true,否则返回false,127.0.0.1代表本机
        return isPortUsing("127.0.0.1", port);
    }

    /**
     * 测试主机Host的port端口是否被使用
     *
     * @param host host
     * @param port port
     * @throws UnknownHostException
     */
    public static boolean isPortUsing(String host, int port) {
        boolean flag = false;
        InetAddress Address;
        try {
            Address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return false;
        }
        try (Socket ignored = new Socket(Address, port)) {
            //建立一个Socket连接
            flag = true;
        } catch (IOException ignoredException) {
        }
        return flag;
    }

    /**
     * 获取实体类匹配的json字段组成的json
     *
     * @param jsonObject 要解析的json
     * @param clazz      对应的实体类
     * @return 子json
     */
    public static JsonObject getSubJsonForEntity(JsonObject jsonObject, Class<?> clazz) {
        JsonObject data = new JsonObject();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (jsonObject.containsKey(field.getName())) {
                data.put(field.getName(), jsonObject.getValue(field.getName()));
            }
        }
        return data;
    }

    /**
     * 注册枚举转换器
     *
     * @param enums 枚举类
     */
    @SafeVarargs
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void enumConvert(Class<? extends Enum>... enums) {
        for (Class<? extends Enum> anEnum : enums) {
            ConvertUtils.register(new Converter() {
                public Object convert(Class type, Object value) {
                    return Enum.valueOf(anEnum, (String) value);
                }
            }, anEnum);
        }
    }

    /**
     * 处理其他配置
     *
     * @param configName configName
     */
    public static void initConfig(String configName, Class<?> tClass) {
        URL resource = tClass.getResource("/conf/" + configName);
        if (resource == null) throw new RuntimeException("路径不存在");
        Buffer buffer = VertxHolder.getVertxInstance().fileSystem().readFileBlocking(resource.getPath());
        JsonObject entries = new JsonObject(buffer);
        Map<String, Object> map = entries.getMap();
        LocalConstant.put(configName, map);
        LOGGER.info("读取配置{}成功", configName);
    }

    public static <T> Set<T> sortClassSet(Set<Class<? extends T>> set) {
        return set.stream().filter(c1 -> {
            HandleSortFilter s1 = c1.getAnnotation(HandleSortFilter.class);
            if (s1 != null) {
                return s1.value() > 0;
            } else {
                return true;
            }
        }).sorted((c1, c2) -> {
            HandleSortFilter s1 = c1.getAnnotation(HandleSortFilter.class);
            HandleSortFilter s2 = c2.getAnnotation(HandleSortFilter.class);
            int n1 = 0, n2 = 0;
            if (s1 != null) {
                n1 = s1.value();
            }
            if (s2 != null) {
                n2 = s2.value();
            }
            return n1 - n2;
        }).map(c -> {
            try {
                return ReflectionUtil.newWithNoParam(c);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());
    }
}
