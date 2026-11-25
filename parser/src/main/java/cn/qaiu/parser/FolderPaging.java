package cn.qaiu.parser;

import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.json.JsonObject;

import java.util.Locale;
import java.util.Set;

/**
 * 目录列表分页约定（解析器与 API 共用）。
 * <p>
 * 入参写在 {@link ShareLinkInfo#getOtherParam()}：
 * <ul>
 *     <li>{@link #PAGE}：从 1 开始的页码；不传时解析器按原逻辑只取第一页</li>
 *     <li>{@link #CURSOR}：上一页返回的 {@code nextCursor}，可选，只是加速，不可信</li>
 * </ul>
 * 解析器取完一页后写回 {@link #PAGE_INFO}，见 {@link #pageInfo}。
 * 没写 {@link #PAGE_INFO} 的解析器视为不分页，整份列表就是第 1 页。
 */
public final class FolderPaging {

    public static final String PAGE = "page";
    public static final String CURSOR = "pageCursor";
    public static final String PAGE_INFO = "pageInfo";

    /** 页码上限。蓝奏无游标时要从第 1 页顺序翻到目标页，必须有界。 */
    public static final int MAX_PAGE = 500;

    private static final Set<String> PAGED_TYPES = Set.of("lz", "fj", "iz");

    private FolderPaging() {
    }

    /** 该网盘的目录解析是否支持按页取。 */
    public static boolean supports(String type) {
        return type != null && PAGED_TYPES.contains(type.toLowerCase(Locale.ROOT));
    }

    /**
     * 解析页码参数。空值返回 null（保持旧行为）；非法值抛 IllegalArgumentException。
     */
    public static Integer parsePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int page;
        try {
            page = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("page 必须是正整数: " + raw);
        }
        if (page < 1 || page > MAX_PAGE) {
            throw new IllegalArgumentException("page 超出范围 1-" + MAX_PAGE + ": " + raw);
        }
        return page;
    }

    /** 本次请求的页码；没传返回 null。 */
    public static Integer requestedPage(ShareLinkInfo info) {
        Object v = info.getOtherParam().get(PAGE);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return v == null ? null : parsePage(v.toString());
    }

    /** 本次请求的页码；没传按第 1 页。 */
    public static int pageOrFirst(ShareLinkInfo info) {
        Integer page = requestedPage(info);
        return page == null ? 1 : page;
    }

    public static String cursor(ShareLinkInfo info) {
        Object v = info.getOtherParam().get(CURSOR);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }

    /**
     * 写回分页结果。
     *
     * @param pageSize   上游每页条数（原始条数口径，含被过滤的占位项）
     * @param hasMore    上游明确还有下一页
     * @param total      上游给出的总条数，未知传 null
     * @param totalPage  上游给出的总页数，未知传 null
     * @param nextCursor 取下一页可直接复用的游标，没有传 null
     */
    public static JsonObject pageInfo(ShareLinkInfo info, int page, int pageSize, boolean hasMore,
                                      Integer total, Integer totalPage, String nextCursor) {
        JsonObject json = new JsonObject()
                .put("paged", true)
                .put("page", page)
                .put("pageSize", pageSize)
                .put("hasMore", hasMore)
                .put("nextPage", hasMore ? page + 1 : null)
                .put("total", total)
                .put("totalPage", totalPage)
                .put("nextCursor", hasMore ? nextCursor : null);
        info.getOtherParam().put(PAGE_INFO, json);
        return json;
    }

    /**
     * 上游给了总页数就以它为准；没给时整页满才认为可能还有。空页一律结束，避免无限翻页。
     */
    public static boolean hasMore(int page, int rawCount, int pageSize, Integer totalPage) {
        if (rawCount <= 0) {
            return false;
        }
        if (totalPage != null && totalPage > 0) {
            return page < totalPage;
        }
        return rawCount >= pageSize;
    }

    /** 小飞机 / 蓝奏优享 share/list 响应：offset 为页码，带 total / totalPage。 */
    public static JsonObject offsetListPage(ShareLinkInfo info, JsonObject resp, int rawCount, int pageSize) {
        int page = pageOrFirst(info);
        Integer total = intOrNull(resp, "total");
        Integer totalPage = intOrNull(resp, "totalPage");
        return pageInfo(info, page, pageSize, hasMore(page, rawCount, pageSize, totalPage), total, totalPage, null);
    }

    private static Integer intOrNull(JsonObject json, String key) {
        if (json == null) {
            return null;
        }
        Object v = json.getValue(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof CharSequence s) {
            try {
                return Integer.parseInt(s.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static JsonObject pageInfo(ShareLinkInfo info) {
        Object v = info.getOtherParam().get(PAGE_INFO);
        return v instanceof JsonObject json ? json : null;
    }
}
