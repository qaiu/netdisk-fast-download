package cn.qaiu.lz.web.model;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.parser.FolderPaging;
import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * /v2/getFileListPage 的返回体。list 与 /v2/getFileList 的 data 结构一致。
 * paged=false 表示该网盘不支持分页，list 已是完整目录，不要再请求下一页。
 */
@Data
@Builder
public class FileListPageResp {
    private List<FileInfo> list;
    private boolean paged;
    private int page;
    private Integer pageSize;
    private boolean hasMore;
    private Integer nextPage;
    private String nextCursor;
    private Integer total;
    private Integer totalPage;

    public static FileListPageResp of(List<FileInfo> list, int page, JsonObject pageInfo) {
        if (pageInfo == null) {
            return FileListPageResp.builder().list(list).paged(false).page(page).hasMore(false).build();
        }
        return FileListPageResp.builder()
                .list(list)
                .paged(true)
                .page(pageInfo.getInteger("page", page))
                .pageSize(pageInfo.getInteger("pageSize"))
                .hasMore(Boolean.TRUE.equals(pageInfo.getBoolean("hasMore")))
                .nextPage(pageInfo.getInteger("nextPage"))
                .nextCursor(pageInfo.getString("nextCursor"))
                .total(pageInfo.getInteger("total"))
                .totalPage(pageInfo.getInteger("totalPage"))
                .build();
    }

    /** 不分页网盘的第 2 页及以后：返回空页，避免客户端重复追加同一份列表。 */
    public static FileListPageResp beyondUnpaged(int page) {
        return FileListPageResp.builder().list(List.of()).paged(false).page(page).hasMore(false).build();
    }

    public static boolean isPagedType(String type) {
        return FolderPaging.supports(type);
    }
}
