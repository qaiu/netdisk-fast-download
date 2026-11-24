package cn.qaiu.parser.impl;

/**
 * 把网页分享 key 转成官方客户端 xtlink。
 * 免密是 {@code xturl{userid}-{f|d}{id}[-checksum]}，
 * 带密码是 {@code xtc{userid}-{f|d}{id}-{checksum}-{password}}。
 */
final class CtfileXtlink {

    private CtfileXtlink() {
    }

    static String fromShare(String shareKey, String password, boolean directory) {
        if (shareKey == null || shareKey.isBlank()) {
            throw new IllegalArgumentException("分享标识为空，无法生成 xtlink");
        }
        String key = shareKey.trim();
        if (key.startsWith("ctfile://")) {
            key = key.substring("ctfile://".length());
        }
        if (key.startsWith("xturl") || key.startsWith("xtc")) {
            return key;
        }
        if (key.startsWith("t-")) {
            return null;
        }
        String[] parts = key.split("-");
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("分享标识无法转换成 xtlink: " + shareKey);
        }
        String userId = parts[0];
        String rawId = parts[1];
        String checksum = parts.length >= 3 ? parts[2] : "";
        if (!rawId.matches("[fd]\\d+")) {
            rawId = (directory ? "d" : "f") + rawId;
        }
        boolean hasPassword = password != null && !password.isBlank();
        if (!hasPassword) {
            return checksum.isEmpty()
                    ? "xturl" + userId + "-" + rawId
                    : "xturl" + userId + "-" + rawId + "-" + checksum;
        }
        if (checksum.isEmpty()) {
            return "xtc" + userId + "-" + rawId + "-" + password;
        }
        return "xtc" + userId + "-" + rawId + "-" + checksum + "-" + password;
    }

    static String prefixedId(String fileId, boolean directory) {
        if (fileId == null || fileId.isBlank()) {
            return "";
        }
        String value = fileId.trim();
        if (value.matches("[fd]\\d+")) {
            return value;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return value;
        }
        return (directory || value.startsWith("d") ? "d" : "f") + digits;
    }

    static String bareId(String fileId) {
        String prefixed = prefixedId(fileId, false);
        if (prefixed.matches("[fd]\\d+")) {
            return prefixed.substring(1);
        }
        return prefixed;
    }
}
