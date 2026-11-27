package cn.qaiu.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ShareUrlParamsTest {

    @Test
    public void cleanShareKeyStripsQueryAndHash() {
        assertEquals("SjfEaY2z", ShareUrlParams.cleanShareKey("SjfEaY2z?code=064d"));
        assertEquals("lN7UVv-pbYJ", ShareUrlParams.cleanShareKey("lN7UVv-pbYJ?pwd=abcd#/list"));
        assertEquals("iabc", ShareUrlParams.cleanShareKey("iabc?webpage=wp1&pwd=secret"));
        assertEquals("s/someShareKey", ShareUrlParams.cleanShareKey("s/someShareKey"));
        assertEquals("key", ShareUrlParams.cleanShareKey("key"));
        assertNull(ShareUrlParams.cleanShareKey(null));
        assertEquals("", ShareUrlParams.cleanShareKey(""));
    }

    @Test
    public void extractPasswordFromCommonQueryNames() {
        assertEquals("064d", ShareUrlParams.extractSharePassword("https://share.feijipan.com/s/SjfEaY2z?code=064d"));
        assertEquals("Pbdl", ShareUrlParams.extractSharePassword("https://www.123684.com/s/6gXfvd-7H9kh?pwd=Pbdl#/list"));
        assertEquals("secret", ShareUrlParams.extractSharePassword(
                "https://a.lanzouw.com/iabc?webpage=wp1&pwd=secret"));
        assertEquals("I4KG7H", ShareUrlParams.extractSharePassword(
                "https://www.vyuyun.com/s/QMa6ie?password=I4KG7H"));
        assertEquals("测码", ShareUrlParams.extractSharePassword("https://example.com/s/k?pwd=%E6%B5%8B%E7%A0%81"));
        assertNull(ShareUrlParams.extractSharePassword("https://drive.uc.cn/s/abc?public=1#/list/share"));
        assertNull(ShareUrlParams.extractSharePassword("https://www.123pan.com/s/abc"));
        assertNull(ShareUrlParams.extractSharePassword("https://www.123pan.com/s/abc?pwd="));
    }

    @Test
    public void prefersPwdOverCode() {
        assertEquals("fromPwd", ShareUrlParams.extractSharePassword(
                "https://example.com/s/k?code=fromCode&pwd=fromPwd"));
    }
}
