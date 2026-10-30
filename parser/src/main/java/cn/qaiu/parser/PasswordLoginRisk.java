package cn.qaiu.parser;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * 账号密码登录撞上验证码/风控时的用户提示。
 * 只识别失败形态并引导改用 Authorization，不实现验证码求解、扫码或重试。
 */
public final class PasswordLoginRisk {

    /**
     * 123 云盘（ye）账号密码登录被验证码/风控拦住时返回给调用方的固定说明。
     * 上游 code/message 会另附在这句话后面，便于和“密码错误”区分。
     */
    public static final String AUTHORIZATION_HINT =
            "账号密码登录触发了验证码/风控，已停止用户名密码登录（不会尝试验证码）。"
                    + "请改为配置 Authorization（浏览器登录后的 Bearer token / JWT，"
                    + "例如 localStorage 的 authorToken、请求头 authorization 或 Cookie 的 sso-token）。"
                    + "静态配置写在 app-dev.yml 的 auths.ye.authorization（与 auths.ye.token 等价）；"
                    + "临时请求使用 authType=authorization 或 authType=accesstoken，token 填该令牌（不要重复带 Bearer 前缀）。"
                    + "说明见 parser/doc/auth-param/。";

    /**
     * 上游文案命中这些片段时视为验证码/风控，而不是单纯的密码错误。
     * 依据公开客户端（AList/OpenList）实际展示过的 123 登录失败文案，以及登录页上的滑块/人机验证提示。
     */
    private static final String[] RISK_KEYWORDS = {
            "验证码",
            "风控",
            "安全风险",
            "安全验证",
            "安全校验",
            "滑块",
            "滑动验证",
            "拼图验证",
            "短信验证",
            "微信登录",
            "微信进行登录",
            "境外登录",
            "需要验证",
            "请验证",
            "请你验证",
            "进行验证",
            "完成验证",
            "二次验证",
            "人机验证",
            "操作频繁",
            "请求过于频繁",
            "请求频繁",
            "captcha",
            "recaptcha",
            "risk control"
    };

    private PasswordLoginRisk() {
    }

    public static boolean isLoginSuccess(JsonObject json) {
        if (json == null) {
            return false;
        }
        Integer code = readCode(json);
        return code != null && code == 200 && StringUtils.isNotBlank(readToken(json));
    }

    public static boolean indicatesCaptchaOrRisk(JsonObject json) {
        if (json == null) {
            return false;
        }
        if (hasTruthyRiskField(json) || hasTruthyRiskField(dataObject(json))) {
            return true;
        }
        return textIndicatesCaptchaOrRisk(collectMessageText(json));
    }

    public static boolean textIndicatesCaptchaOrRisk(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : RISK_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 非成功登录响应的用户可见说明。验证码/风控走 Authorization 引导，其余保持“登录失败: 上游文案”。
     */
    public static String describeLoginFailure(JsonObject json) {
        if (json == null) {
            return "登录响应格式异常";
        }
        if (indicatesCaptchaOrRisk(json)) {
            return AUTHORIZATION_HINT + " 上游返回：" + upstreamSummary(json);
        }
        Integer code = readCode(json);
        if (code != null && code == 200) {
            return "未获取到token";
        }
        String message = firstMessage(json);
        if (StringUtils.isBlank(message)) {
            return "登录失败";
        }
        return "登录失败: " + message;
    }

    public static String nonJsonLoginFailure(String body) {
        if (textIndicatesCaptchaOrRisk(body)) {
            return AUTHORIZATION_HINT + " 上游返回：" + truncate(body, 300);
        }
        return "登录响应格式异常: " + body;
    }

    private static String collectMessageText(JsonObject json) {
        StringBuilder text = new StringBuilder();
        appendMessage(text, json);
        JsonObject data = dataObject(json);
        if (data != null) {
            appendMessage(text, data);
        }
        return text.toString();
    }

    private static void appendMessage(StringBuilder text, JsonObject json) {
        append(text, json.getString("message"));
        append(text, json.getString("msg"));
    }

    private static void append(StringBuilder text, String value) {
        if (StringUtils.isNotBlank(value)) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(value);
        }
    }

    private static boolean hasTruthyRiskField(JsonObject obj) {
        if (obj == null) {
            return false;
        }
        for (String key : obj.fieldNames()) {
            if (!isRiskFieldName(key)) {
                continue;
            }
            if (isTruthyRiskValue(obj.getValue(key))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRiskFieldName(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("captcha")
                || lower.contains("vcode")
                || lower.contains("risk")
                || "needverify".equals(lower)
                || "need_verify".equals(lower)
                || "slideverify".equals(lower)
                || "verifycode".equals(lower)
                || "verify_code".equals(lower);
    }

    private static boolean isTruthyRiskValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0d;
        }
        if (value instanceof CharSequence) {
            String text = value.toString().trim();
            if (text.isEmpty()) {
                return false;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            return !"false".equals(lower) && !"0".equals(lower) && !"null".equals(lower) && !"none".equals(lower);
        }
        if (value instanceof JsonObject) {
            return !((JsonObject) value).isEmpty();
        }
        if (value instanceof JsonArray) {
            return !((JsonArray) value).isEmpty();
        }
        return true;
    }

    private static String upstreamSummary(JsonObject json) {
        StringBuilder summary = new StringBuilder();
        Integer code = readCode(json);
        if (code != null) {
            summary.append("code=").append(code);
        }
        String message = firstMessage(json);
        if (StringUtils.isNotBlank(message)) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append("message=").append(message);
        }
        if (summary.length() == 0) {
            summary.append(json.encode());
        }
        return truncate(summary.toString(), 300);
    }

    private static String firstMessage(JsonObject json) {
        String message = json.getString("message");
        if (StringUtils.isBlank(message)) {
            message = json.getString("msg");
        }
        if (StringUtils.isBlank(message)) {
            JsonObject data = dataObject(json);
            if (data != null) {
                message = data.getString("message");
                if (StringUtils.isBlank(message)) {
                    message = data.getString("msg");
                }
            }
        }
        return message;
    }

    static String readToken(JsonObject json) {
        JsonObject data = dataObject(json);
        if (data == null) {
            return null;
        }
        Object token = data.getValue("token");
        return token == null ? null : token.toString();
    }

    private static JsonObject dataObject(JsonObject json) {
        Object data = json.getValue("data");
        return data instanceof JsonObject ? (JsonObject) data : null;
    }

    static Integer readCode(JsonObject json) {
        Object code = json.getValue("code");
        if (code instanceof Number) {
            return ((Number) code).intValue();
        }
        if (code instanceof String && StringUtils.isNumeric(((String) code).trim())) {
            return Integer.parseInt(((String) code).trim());
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "...";
    }
}
