package cn.qaiu.util;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 123分享链接子域工具：将分享 key 的前半段解码为数字 uid。
 */
public final class YeShareHostUtil {

    private static final String CODE62 = "Tvd3hHA9QEkom14xpfaBJIMwgFYGPXn2sWCNORDr80KuUSl7bZcetizL5q6yVj";
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;
    private static final Map<Character, Integer> DECODE_MAP = new HashMap<>();

    static {
        for (int i = 0; i < CODE62.length(); i++) {
            DECODE_MAP.put(CODE62.charAt(i), i);
        }
    }

    private YeShareHostUtil() {
    }

    public static String getNumericSubdomainIdByShareKey(String shareKey) {
        String normalized = normalizeShareKey(shareKey);
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        int split = normalized.indexOf('-');
        if (split <= 0) {
            return "";
        }
        String encodedUid = normalized.substring(0, split);
        Long uid = decodeBase62LittleEndian(encodedUid);
        return uid == null ? "" : String.valueOf(uid);
    }

    public static String normalizeShareKey(String shareKey) {
        if (StringUtils.isBlank(shareKey)) {
            return "";
        }
        String key = shareKey.trim();
        int queryIndex = key.indexOf('?');
        if (queryIndex >= 0) {
            key = key.substring(0, queryIndex);
        }
        int slashIndex = key.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < key.length() - 1) {
            key = key.substring(slashIndex + 1);
        }
        if (key.endsWith(".html")) {
            key = key.substring(0, key.length() - 5);
        }
        return key;
    }

    private static Long decodeBase62LittleEndian(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        long result = 0L;
        for (int i = 0; i < value.length(); i++) {
            Integer digit = DECODE_MAP.get(value.charAt(i));
            if (digit == null) {
                return null;
            }
            double weighted = digit * Math.pow(62, i);
            if (!Double.isFinite(weighted)) {
                return null;
            }
            long next = result + (long) weighted;
            if (next <= 0 || next > MAX_SAFE_INTEGER) {
                return null;
            }
            result = next;
        }
        if (result <= 0) {
            return null;
        }
        return result;
    }
}
