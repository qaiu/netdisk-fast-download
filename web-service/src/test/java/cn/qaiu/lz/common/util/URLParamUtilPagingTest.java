package cn.qaiu.lz.common.util;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

/** 分页参数不能被当作截断的分享链接参数拼回 url，否则会改变分享链接与解析结果。 */
public class URLParamUtilPagingTest {

    @Test
    public void pagingParamsAreNotAppendedToShareUrl() {
        MultiMap params = MultiMap.caseInsensitiveMultiMap()
                .add("url", "https://pan.lanzoui.com/b66477")
                .add("pwd", "1234")
                .add("page", "2")
                .add("pageCursor", "eyJ2IjoxfQ");
        assertEquals("https://pan.lanzoui.com/b66477", URLParamUtil.parserParams(request(params)));
    }

    @Test
    public void truncatedShareParamsStillAppended() {
        MultiMap params = MultiMap.caseInsensitiveMultiMap()
                .add("url", "https://moteam.lanzoum.com/b00yaejvte?a=1")
                .add("webpage", "x")
                .add("page", "3");
        assertEquals("https://moteam.lanzoum.com/b00yaejvte?a=1&webpage=x", URLParamUtil.parserParams(request(params)));
    }

    private static HttpServerRequest request(MultiMap params) {
        return (HttpServerRequest) Proxy.newProxyInstance(HttpServerRequest.class.getClassLoader(),
                new Class<?>[]{HttpServerRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "params" -> params;
                    case "absoluteURI" -> "http://localhost/v2/getFileListPage";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
