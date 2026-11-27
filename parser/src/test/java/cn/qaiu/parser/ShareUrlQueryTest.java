package cn.qaiu.parser;

import cn.qaiu.entity.ShareLinkInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 完整分享链接里的提取码不应被吞进 shareKey。
 */
public class ShareUrlQueryTest {

    @Test
    public void feijipanCodeQueryBecomesPassword() {
        ShareLinkInfo info = ParserCreate.fromShareUrl("https://share.feijipan.com/s/SjfEaY2z?code=064d")
                .getShareLinkInfo();
        assertEquals("fj", info.getType());
        assertEquals("SjfEaY2z", info.getShareKey());
        assertEquals("064d", info.getSharePassword());
        assertTrue(info.getStandardUrl().contains("SjfEaY2z"));
        assertTrue("standard url should not keep the query in the key",
                !info.getStandardUrl().contains("code=064d") || !info.getStandardUrl().contains("SjfEaY2z?"));
    }

    @Test
    public void yePwdQueryIsExtractedWithoutChangingKey() {
        ShareLinkInfo info = ParserCreate.fromShareUrl("https://www.123278.com/s/lN7UVv-pbYJ.html?pwd=abcd")
                .getShareLinkInfo();
        assertEquals("ye", info.getType());
        assertEquals("lN7UVv-pbYJ", info.getShareKey());
        assertEquals("abcd", info.getSharePassword());
    }

    @Test
    public void lanzouQueryPasswordAndWebpageDoNotPolluteKey() {
        ShareLinkInfo info = ParserCreate.fromShareUrl("https://a.lanzouw.com/iabc?webpage=wp1&pwd=secret")
                .getShareLinkInfo();
        assertEquals("lz", info.getType());
        assertEquals("iabc", info.getShareKey());
        assertEquals("secret", info.getSharePassword());
        assertTrue(info.getShareUrl().contains("webpage=wp1"));
    }

    @Test
    public void ilanzouHashAndCodeQuery() {
        ShareLinkInfo info = ParserCreate.fromShareUrl("https://www.ilanzou.com/s/RkR09zIL?code=abc#/list")
                .getShareLinkInfo();
        assertEquals("iz", info.getType());
        assertEquals("RkR09zIL", info.getShareKey());
        assertEquals("abc", info.getSharePassword());
    }

    @Test
    public void ucPublicQueryIsNotTreatedAsPassword() {
        ShareLinkInfo info = ParserCreate.fromShareUrl("https://drive.uc.cn/s/e623b6da278e4?public=1#/list/share")
                .getShareLinkInfo();
        assertEquals("uc", info.getType());
        assertEquals("e623b6da278e4", info.getShareKey());
        assertTrue(info.getSharePassword() == null || info.getSharePassword().isEmpty());
    }

    @Test
    public void plainLanzouUrlUnchanged() {
        ShareLinkInfo info = ParserCreate.fromShareUrl("https://wwn.lanzouy.com/ihLkw1gezutg")
                .getShareLinkInfo();
        assertEquals("lz", info.getType());
        assertEquals("ihLkw1gezutg", info.getShareKey());
        assertTrue(info.getSharePassword() == null || info.getSharePassword().isEmpty());
    }
}
