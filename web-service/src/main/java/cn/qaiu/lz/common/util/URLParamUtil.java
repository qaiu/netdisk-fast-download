package cn.qaiu.lz.common.util;

import cn.qaiu.parser.ParserCreate;
import cn.qaiu.vx.core.util.ConfigConstant;
import cn.qaiu.vx.core.util.SharedDataUtil;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 处理URL截断问题，拼接被截断的参数，特殊处理pwd参数。
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2024/9/13
 */
@Slf4j
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

        // 拼接被截断的URL参数，忽略pwd、auth等参数
        StringBuilder urlBuilder = new StringBuilder(decodedUrl);
        boolean firstParam = !decodedUrl.contains("?");

        for (String paramName : params.names()) {
            // 忽略 "url", "pwd", "dirId", "uuid", "auth" 参数（这些参数单独处理，不应拼接到分享URL中）
            if (!paramName.equals("url") && !paramName.equals("pwd") && !paramName.equals("dirId") 
                    && !paramName.equals("uuid") && !paramName.equals("auth")) {
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

    /**
     * 添加临时认证参数（一次性，不保存到数据库或共享内存）
     * 如果提供了临时认证参数，将覆盖后台配置的认证信息
     * 
     * @param parserCreate ParserCreate对象
     * @param authType 认证类型
     * @param authToken 认证token/用户名/accesstoken/cookie
     * @param authPassword 密码（仅用于username_password认证）
     * @param authInfo1-5 扩展认证信息（用于custom认证）
     */
    public static void addTempAuthParam(ParserCreate parserCreate, String authType, 
                                         String authToken, String authPassword,
                                         String authInfo1, String authInfo2, String authInfo3,
                                         String authInfo4, String authInfo5) {
        if (StringUtils.isBlank(authType) && StringUtils.isBlank(authToken)) {
            // 没有提供临时认证参数，使用后台配置
            addParam(parserCreate);
            return;
        }

        // 先添加代理配置和域名配置
        LocalMap<Object, Object> localMap = VertxHolder.getVertxInstance().sharedData()
                .getLocalMap(ConfigConstant.LOCAL);
        String type = parserCreate.getShareLinkInfo().getType();
        
        if (localMap.containsKey(ConfigConstant.PROXY)) {
            JsonObject proxy = (JsonObject) localMap.get(ConfigConstant.PROXY);
            if (proxy.containsKey(type)) {
                parserCreate.getShareLinkInfo().getOtherParam().put(ConfigConstant.PROXY, proxy.getJsonObject(type));
            }
        }
        
        String linkPrefix = SharedDataUtil.getJsonConfig("server").getString("domainName");
        parserCreate.getShareLinkInfo().getOtherParam().put("domainName", linkPrefix);

        // 构建临时认证信息
        MultiMap tempAuth = MultiMap.caseInsensitiveMultiMap();
        
        if (StringUtils.isNotBlank(authType)) {
            tempAuth.set("authType", authType.trim());
        }
        
        String authTypeValue = authType != null ? authType : "";
        switch (authTypeValue.toLowerCase()) {
            case "accesstoken":
            case "authorization":
                if (StringUtils.isNotBlank(authToken)) {
                    tempAuth.set("token", authToken.trim());
                }
                break;
                
            case "cookie":
                // cookie 类型需要同时设置 token 和 cookie 字段
                // QkTool/UcTool 等从 auths.get("cookie") 获取 cookie 值
                if (StringUtils.isNotBlank(authToken)) {
                    tempAuth.set("token", authToken.trim());
                    tempAuth.set("cookie", authToken.trim());
                }
                break;
                
            case "password":
            case "username_password":
                if (StringUtils.isNotBlank(authToken)) {
                    tempAuth.set("username", authToken.trim());
                    tempAuth.set("token", authToken.trim()); // 兼容旧的解析器
                }
                if (StringUtils.isNotBlank(authPassword)) {
                    tempAuth.set("password", authPassword.trim());
                }
                break;
                
            case "custom":
                // 自定义认证支持多个扩展字段
                if (StringUtils.isNotBlank(authToken)) {
                    tempAuth.set("token", authToken.trim());
                }
                if (StringUtils.isNotBlank(authInfo1)) {
                    parseAndSetAuthInfo(tempAuth, authInfo1);
                }
                if (StringUtils.isNotBlank(authInfo2)) {
                    parseAndSetAuthInfo(tempAuth, authInfo2);
                }
                if (StringUtils.isNotBlank(authInfo3)) {
                    parseAndSetAuthInfo(tempAuth, authInfo3);
                }
                if (StringUtils.isNotBlank(authInfo4)) {
                    parseAndSetAuthInfo(tempAuth, authInfo4);
                }
                if (StringUtils.isNotBlank(authInfo5)) {
                    parseAndSetAuthInfo(tempAuth, authInfo5);
                }
                break;
                
            default:
                // 默认处理：将authToken作为token
                if (StringUtils.isNotBlank(authToken)) {
                    tempAuth.set("token", authToken.trim());
                }
                break;
        }
        
        // 设置临时认证信息（覆盖后台配置）
        if (!tempAuth.isEmpty()) {
            parserCreate.getShareLinkInfo().getOtherParam().put(ConfigConstant.AUTHS, tempAuth);
            // 设置标记表示已添加临时认证
            parserCreate.getShareLinkInfo().getOtherParam().put("__TEMP_AUTH_ADDED", true);
            log.debug("已添加临时认证参数: diskType={}, authType={}", type, authType);
        } else {
            // 如果没有有效的临时认证参数，回退到使用后台配置
            if (localMap.containsKey(ConfigConstant.AUTHS)) {
                JsonObject auths = (JsonObject) localMap.get(ConfigConstant.AUTHS);
                if (auths.containsKey(type)) {
                    MultiMap entries = MultiMap.caseInsensitiveMultiMap();
                    JsonObject jsonObject = auths.getJsonObject(type);
                    if (jsonObject != null) {
                        jsonObject.forEach(entity -> {
                            if (entity == null || entity.getValue() == null) {
                                return;
                            }
                            if (StringUtils.isEmpty(entity.getKey()) || StringUtils.isEmpty(entity.getValue().toString())) {
                                return;
                            }
                            entries.set(StringUtils.trim(entity.getKey()), StringUtils.trim(entity.getValue().toString()));
                        });
                    }
                    parserCreate.getShareLinkInfo().getOtherParam().put(ConfigConstant.AUTHS, entries);
                }
            }
        }
    }

    /**
     * 解析并设置认证信息（格式: key:value）
     */
    private static void parseAndSetAuthInfo(MultiMap authMap, String authInfo) {
        if (StringUtils.isBlank(authInfo)) {
            return;
        }
        String[] parts = authInfo.split(":", 2);
        if (parts.length == 2) {
            String key = parts[0].trim();
            String value = parts[1].trim();
            if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
                authMap.set(key, value);
            }
        }
    }
}
