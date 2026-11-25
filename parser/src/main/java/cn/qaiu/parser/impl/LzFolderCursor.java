package cn.qaiu.parser.impl;

import cn.qaiu.util.CommonUtils;
import io.vertx.core.json.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 蓝奏目录「下一页」游标。
 * <p>
 * filemoreajax 的 t/k 由分享页下发，上游按这组令牌记录已翻到第几页：同一组令牌可以取
 * 「已取过的最大页 + 1」以内的任意页；换一组新令牌直接跳到第 N 页会返回 zt=4「请刷新，重试」。
 * 所以加载更多时把上一页用过的表单（不含密码和页码）交还给客户端，下次直接续翻。
 * <p>
 * 游标来自客户端，不可信：只用于表单字段，POST 主机仍由服务端按分享链接决定；
 * 分享 key、页码、路径对不上一律当作没有游标，回退到从第 1 页顺序翻。
 */
final class LzFolderCursor {

    private static final int VERSION = 1;
    private static final Pattern PATH = Pattern.compile("/filemoreajax\\.php\\?file=(\\d+)");
    private static final Pattern KEY = Pattern.compile("\\w{1,32}");
    private static final int MAX_VALUE_LENGTH = 256;
    private static final int MAX_FIELDS = 32;

    final String path;
    final Map<String, String> form;
    final String webpage;

    private LzFolderCursor(String path, Map<String, String> form, String webpage) {
        this.path = path;
        this.form = form;
        this.webpage = webpage;
    }

    static String encode(String shareKey, String path, Map<String, String> form, int nextPage, String webpage) {
        JsonObject f = new JsonObject();
        form.forEach((k, v) -> {
            if (!"pwd".equals(k) && !"pg".equals(k)) {
                f.put(k, v);
            }
        });
        JsonObject json = new JsonObject()
                .put("v", VERSION)
                .put("s", shareKey)
                .put("p", path)
                .put("n", nextPage)
                .put("f", f);
        if (webpage != null && !webpage.isBlank()) {
            json.put("w", webpage);
        }
        return CommonUtils.urlBase64Encode(json.encode());
    }

    /**
     * @return 校验通过的游标；任何不匹配或格式问题都返回 null
     */
    static LzFolderCursor decode(String raw, String shareKey, int page) {
        if (raw == null || raw.isBlank() || raw.length() > 4096) {
            return null;
        }
        try {
            JsonObject json = new JsonObject(CommonUtils.urlBase64Decode(raw));
            if (json.getInteger("v", 0) != VERSION) {
                return null;
            }
            if (shareKey == null || !shareKey.equals(json.getString("s"))) {
                return null;
            }
            if (json.getInteger("n", -1) != page) {
                return null;
            }
            String path = json.getString("p");
            var m = path == null ? null : PATH.matcher(path);
            if (m == null || !m.matches()) {
                return null;
            }
            JsonObject f = json.getJsonObject("f");
            if (f == null || f.size() > MAX_FIELDS) {
                return null;
            }
            Map<String, String> form = new LinkedHashMap<>();
            for (String key : f.fieldNames()) {
                Object v = f.getValue(key);
                if (!KEY.matcher(key).matches() || v == null) {
                    return null;
                }
                String s = String.valueOf(v);
                if (s.length() > MAX_VALUE_LENGTH || s.chars().anyMatch(c -> c < 0x20)) {
                    return null;
                }
                form.put(key, s);
            }
            if (!m.group(1).equals(form.get("fid")) || !form.containsKey("t") || !form.containsKey("k")) {
                return null;
            }
            String webpage = json.getString("w");
            if (webpage != null && (webpage.length() > MAX_VALUE_LENGTH || !webpage.matches("[\\w\\-=.%]+"))) {
                webpage = null;
            }
            return new LzFolderCursor(path, form, webpage);
        } catch (Exception e) {
            return null;
        }
    }

    /** 本次请求用的表单：游标字段 + 当前密码 + 目标页。 */
    Map<String, String> formFor(String pwd, int page) {
        Map<String, String> out = new LinkedHashMap<>(form);
        out.put("pg", String.valueOf(page));
        out.put("pwd", pwd == null ? "" : pwd);
        return out;
    }
}
