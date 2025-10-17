package cn.qaiu.lz.common.util;

import cn.qaiu.parser.ParserCreate;
import cn.qaiu.vx.core.util.ConfigConstant;
import cn.qaiu.vx.core.util.SharedDataUtil;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 处理URL截断问题，拼接被截断的参数，特殊处理pwd参数。
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2024/9/13
 */
public class URLParamUtil {

    /**
     * 解析并处理截断的URL参数
     *
     * @param request HttpServerRequest对象
     * @return 完整的URL字符串
     */
    public static String parserParams(HttpServerRequest request) {

        String url = request.absoluteURI();
        MultiMap params = request.params();
        // 处理URL截断的情况，例如: url='https://...&key=...&code=...'
        if (params.contains("url")) {
            String encodedUrl = params.get("url");
            url = handleTruncatedUrl(encodedUrl, params);
        }
        return url;
    }

    /**
     * 处理被截断的URL，拼接所有参数，特殊处理pwd参数。
     *
     * @param encodedUrl 被截断的url参数
     * @param params     请求的其他参数
     * @return 重新拼接后的完整URL
     */
    private static String handleTruncatedUrl(String encodedUrl, MultiMap params) {
        // 对URL进行解码，以便获取完整的URL
        String decodedUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8);

        // 如果URL已经包含查询参数，不需要额外拼接
        if (params.contains("pwd")) {
            if (params.size() == 2) {
                return decodedUrl;
            }
        } else {
            if (params.size() == 1) {
                return decodedUrl;
            }
        }

        // 拼接被截断的URL参数，忽略pwd参数
        StringBuilder urlBuilder = new StringBuilder(decodedUrl);
        boolean firstParam = !decodedUrl.contains("?");

        for (String paramName : params.names()) {
            if (!paramName.equals("url") && !paramName.equals("pwd") && !paramName.equals("dirId") && !paramName.equals("uuid")) {  // 忽略 "url" 和 "pwd" 参数
                if (firstParam) {
                    urlBuilder.append("?");
                    firstParam = false;
                } else {
                    urlBuilder.append("&");
                }
                urlBuilder.append(paramName).append("=").append(params.get(paramName));
            }
        }

        return urlBuilder.toString();
    }

    /**
     * 添加共享链接的其他参数到ParserCreate对象中
     * @param parserCreate ParserCreate对象，包含共享链接信息
     */
    public static void addParam(ParserCreate parserCreate) {
        LocalMap<Object, Object> localMap = VertxHolder.getVertxInstance().sharedData()
                .getLocalMap(ConfigConstant.LOCAL);

        String type = parserCreate.getShareLinkInfo().getType();
        if (localMap.containsKey(ConfigConstant.PROXY)) {
            JsonObject proxy = (JsonObject) localMap.get(ConfigConstant.PROXY);
            if (proxy.containsKey(type)) {
                parserCreate.getShareLinkInfo().getOtherParam().put(ConfigConstant.PROXY, proxy.getJsonObject(type));
            }
        }
        if (localMap.containsKey(ConfigConstant.AUTHS)) {
            JsonObject auths = (JsonObject) localMap.get(ConfigConstant.AUTHS);
            if (auths.containsKey(type)) {
                // 需要处理引号
                MultiMap entries = MultiMap.caseInsensitiveMultiMap();
                JsonObject jsonObject = auths.getJsonObject(type);
                if (jsonObject != null) {
                    jsonObject.forEach(entity -> {
                        if (entity == null || entity.getValue() == null) {
                            return;
                        }
                        entries.set(entity.getKey(), entity.getValue().toString());
                    });
                }

                parserCreate.getShareLinkInfo().getOtherParam().put(ConfigConstant.AUTHS, entries);
            }
        }

        String linkPrefix = SharedDataUtil.getJsonConfig("server").getString("domainName");
        parserCreate.getShareLinkInfo().getOtherParam().put("domainName", linkPrefix);
    }
}
