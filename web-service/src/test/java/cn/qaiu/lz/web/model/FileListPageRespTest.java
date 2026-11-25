package cn.qaiu.lz.web.model;

import cn.qaiu.entity.FileInfo;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FileListPageRespTest {

    @Test
    public void unpagedProviderReturnsWholeListAsLastPage() {
        List<FileInfo> list = List.of(new FileInfo().setFileName("a"));
        FileListPageResp resp = FileListPageResp.of(list, 1, null);
        assertFalse(resp.isPaged());
        assertFalse(resp.isHasMore());
        assertEquals(1, resp.getList().size());
        assertNull(resp.getNextCursor());
    }

    @Test
    public void unpagedProviderBeyondFirstPageIsEmpty() {
        FileListPageResp resp = FileListPageResp.beyondUnpaged(2);
        assertTrue(resp.getList().isEmpty());
        assertFalse(resp.isHasMore());
        assertEquals(2, resp.getPage());
    }

    @Test
    public void pagedInfoIsCopied() {
        JsonObject info = new JsonObject().put("paged", true).put("page", 2).put("pageSize", 50)
                .put("hasMore", true).put("nextPage", 3).put("nextCursor", "c").putNull("total");
        FileListPageResp resp = FileListPageResp.of(List.of(), 2, info);
        assertTrue(resp.isPaged());
        assertTrue(resp.isHasMore());
        assertEquals(Integer.valueOf(3), resp.getNextPage());
        assertEquals("c", resp.getNextCursor());
        assertNull(resp.getTotal());
        assertTrue(FileListPageResp.isPagedType("lz"));
        assertFalse(FileListPageResp.isPagedType("qk"));
    }
}
