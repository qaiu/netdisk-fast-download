package cn.qaiu.parser.impl;

import io.vertx.core.MultiMap;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 蓝奏云优享 X-Forwarded-For 配置。
 * <p>
 * 读取系统属性 {@code nfd.iz.xff}，其次环境变量 {@code NFD_IZ_XFF}：
 * <ul>
 *     <li>未设置 / 空 / {@code off}：不发送该头（默认）</li>
 *     <li>{@code random}：每次创建解析器时随机一个国内常见网段 IP</li>
 *     <li>其它值：原样作为头的值，例如 {@code 113.96.181.18}</li>
 * </ul>
 */
public final class IzXffHeader {

    public static final String PROP = "nfd.iz.xff";
    public static final String ENV = "NFD_IZ_XFF";
    private static final String HEADER = "X-Forwarded-For";

    /** 国内运营商常见 /8 段首字节 */
    private static final int[] CN_PREFIX = {36, 39, 42, 58, 59, 60, 61, 101, 106, 110, 111, 112, 113, 114,
            115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 175, 180, 182, 183, 202, 210, 211, 218, 219, 220, 221, 222, 223};

    private IzXffHeader() {
    }

    static String configured() {
        String v = System.getProperty(PROP);
        if (StringUtils.isBlank(v)) {
            v = System.getenv(ENV);
        }
        return v == null ? "" : v.trim();
    }

    /** 根据配置得到头的值，返回 null 表示不发送 */
    static String resolve(String mode) {
        if (StringUtils.isBlank(mode) || "off".equalsIgnoreCase(mode)) {
            return null;
        }
        if ("random".equalsIgnoreCase(mode)) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            return CN_PREFIX[r.nextInt(CN_PREFIX.length)] + "." + r.nextInt(256) + "." + r.nextInt(256) + "." + (1 + r.nextInt(254));
        }
        return mode;
    }

    /** 按当前配置写入或移除请求头 */
    public static void apply(MultiMap headers) {
        String value = resolve(configured());
        if (value == null) {
            headers.remove(HEADER);
        } else {
            headers.set(HEADER, value);
        }
    }
}
