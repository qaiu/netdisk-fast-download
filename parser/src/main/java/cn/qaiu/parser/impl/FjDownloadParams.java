package cn.qaiu.parser.impl;

import cn.qaiu.util.AESUtils;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

final class FjDownloadParams {
    private FjDownloadParams() {
    }

    static JsonObject create(String fileId, String userId, String uuid, String shareId, long timestamp) {
        if (StringUtils.isBlank(fileId) || StringUtils.isBlank(shareId)) {
            throw new IllegalArgumentException("缺少 fileId 或 shareId");
        }
        return JsonObject.of("fileId", fileId,
                "fidEncode", AESUtils.encrypt2Hex(fileId + "|" + StringUtils.defaultString(userId)),
                "uuid", uuid, "shareId", shareId,
                "ts", AESUtils.encrypt2Hex(Long.toString(timestamp)),
                "auth", AESUtils.encrypt2Hex(fileId + "|" + timestamp));
    }

    static String fileId(JsonObject params) throws Exception {
        String fileId = params.getString("fileId");
        if (StringUtils.isNotBlank(fileId)) {
            return fileId;
        }
        // 兼容已生成的目录链接，但不再复用其中的身份和过期签名。
        String decoded = AESUtils.decryptByHexAES(params.getString("fidEncode"), AESUtils.CIPHER_AES0);
        int separator = decoded.indexOf('|');
        if (separator <= 0) {
            throw new IllegalArgumentException("无效的 fidEncode");
        }
        return decoded.substring(0, separator);
    }

    static String ownerId(JsonObject params) throws Exception {
        Object id = params.getValue("userId");
        if (id != null && StringUtils.isNotBlank(id.toString())) {
            return id.toString();
        }
        String encoded = params.getString("fidEncode");
        if (StringUtils.isBlank(encoded)) {
            return null;
        }
        String decoded = AESUtils.decryptByHexAES(encoded, AESUtils.CIPHER_AES0);
        int separator = decoded.indexOf('|');
        String owner = separator < 0 ? null : decoded.substring(separator + 1);
        return StringUtils.isBlank(owner) || "null".equals(owner) ? null : owner;
    }

    static String missingLocation(int status, String body) {
        String message = "未获取到下载重定向地址, HTTP " + status;
        if (StringUtils.isBlank(body)) {
            return message + ", 响应体为空";
        }
        try {
            JsonObject json = new JsonObject(body);
            if (StringUtils.isNotBlank(json.getString("msg"))) {
                return message + ", 上游返回: " + preview(json.getString("msg"))
                        + " (code=" + json.getValue("code") + ")";
            }
        } catch (RuntimeException ignored) {
            // 非 JSON 错误页同样保留有限长度的诊断内容。
        }
        return message + ", 响应体: " + preview(body);
    }

    private static String preview(String value) {
        return value.length() > 500 ? value.substring(0, 500) + "..." : value;
    }
}
