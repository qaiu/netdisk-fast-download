package cn.qaiu.parser;

import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FolderPagingTest {

    @Test
    public void blankPageKeepsLegacyBehaviour() {
        assertNull(FolderPaging.parsePage(null));
        assertNull(FolderPaging.parsePage(" "));
        assertEquals(1, FolderPaging.pageOrFirst(info()));
        assertNull(FolderPaging.requestedPage(info()));
    }

    @Test
    public void invalidPagesAreRejected() {
        assertEquals(Integer.valueOf(3), FolderPaging.parsePage(" 3 "));
        assertThrows(IllegalArgumentException.class, () -> FolderPaging.parsePage("0"));
        assertThrows(IllegalArgumentException.class, () -> FolderPaging.parsePage("-1"));
        assertThrows(IllegalArgumentException.class, () -> FolderPaging.parsePage("abc"));
        assertThrows(IllegalArgumentException.class, () -> FolderPaging.parsePage(String.valueOf(FolderPaging.MAX_PAGE + 1)));
    }

    @Test
    public void onlyLzFjIzArePaged() {
        assertTrue(FolderPaging.supports("lz"));
        assertTrue(FolderPaging.supports("FJ"));
        assertTrue(FolderPaging.supports("iz"));
        assertFalse(FolderPaging.supports("qk"));
        assertFalse(FolderPaging.supports(null));
    }

    @Test
    public void emptyPageAlwaysEnds() {
        assertFalse(FolderPaging.hasMore(2, 0, 60, 9));
        assertFalse(FolderPaging.hasMore(1, 0, 50, null));
    }

    @Test
    public void totalPageWinsOverFullPageHeuristic() {
        assertFalse(FolderPaging.hasMore(2, 60, 60, 2));
        assertTrue(FolderPaging.hasMore(1, 5, 60, 2));
        assertTrue(FolderPaging.hasMore(1, 50, 50, null));
        assertFalse(FolderPaging.hasMore(1, 49, 50, null));
        assertTrue(FolderPaging.hasMore(1, 50, 50, 0));
    }

    @Test
    public void offsetListPageReadsTotals() {
        ShareLinkInfo info = info();
        info.getOtherParam().put(FolderPaging.PAGE, 2);
        JsonObject resp = new JsonObject().put("total", "121").put("totalPage", 3).put("offset", 2).put("limit", 60);
        JsonObject page = FolderPaging.offsetListPage(info, resp, 60, 60);
        assertEquals(Integer.valueOf(121), page.getInteger("total"));
        assertEquals(Integer.valueOf(3), page.getInteger("totalPage"));
        assertTrue(page.getBoolean("hasMore"));
        assertEquals(Integer.valueOf(3), page.getInteger("nextPage"));
        assertEquals(page, FolderPaging.pageInfo(info));
    }

    @Test
    public void noMoreClearsNextPageAndCursor() {
        JsonObject page = FolderPaging.pageInfo(info(), 4, 50, false, null, null, "cursor");
        assertNull(page.getValue("nextPage"));
        assertNull(page.getValue("nextCursor"));
    }

    private static ShareLinkInfo info() {
        return ShareLinkInfo.newBuilder().type("lz").shareKey("b1").build();
    }
}
