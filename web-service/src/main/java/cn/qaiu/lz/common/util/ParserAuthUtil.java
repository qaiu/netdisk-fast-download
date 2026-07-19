package cn.qaiu.lz.common.util;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.lz.web.model.AuthParam;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.vx.core.util.ConfigConstant;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@Slf4j
public class ParserAuthUtil {

    public static final String SKIP_CLIENT_LINKS = "_skipClientLinks";
    public static final String TEMP_AUTH_ADDED = "__TEMP_AUTH_ADDED";
    public static final String DONATED_ACCOUNT_TOKEN = "__AUTO_DONATED_ACCOUNT_TOKEN";

    private ParserAuthUtil() {
    }

    public static JsonObject buildOtherParam(HttpServerRequest request, String auth, String requestOrigin) {
        return buildOtherParam(request, auth, requestOrigin, false);
    }

    public static JsonObject buildOtherParam(HttpServerRequest request, String auth, String requestOrigin,
                                             boolean skipClientLinks) {
        JsonObject otherParam = JsonObject.of(
                "UA", request.headers().get("user-agent"),
                "_requestOrigin", requestOrigin
        );
        if (skipClientLinks) {
            otherParam.put(SKIP_CLIENT_LINKS, true);
        }

        if (StringUtils.isNotBlank(auth)) {
            AuthParam authParam = AuthParamCodec.decode(auth);
            if (authParam != null && authParam.hasValidAuth()) {
                otherParam.put("authType", authParam.getAuthType());
                otherParam.put("authToken", authParam.getPrimaryCredential());
                otherParam.put("authPassword", authParam.getPassword());
                otherParam.put("authInfo1", authParam.getExt1());
                otherParam.put("authInfo2", authParam.getExt2());
                otherParam.put("authInfo3", authParam.getExt3());
                otherParam.put("authInfo4", authParam.getExt4());
                otherParam.put("authInfo5", authParam.getExt5());
                if (StringUtils.isNotBlank(authParam.getDonatedAccountToken())) {
                    otherParam.put("donatedAccountToken", authParam.getDonatedAccountToken());
                }
                log.debug("已解码认证参数: authType={}", authParam.getAuthType());
            }
        }

        return otherParam;
    }

    public static Future<Void> applyAuthParamsAndDonatedFallback(ParserCreate parserCreate, JsonObject otherParam,
                                                                 DbService dbService) {
        JsonObject params = otherParam == null ? new JsonObject() : otherParam;
        parserCreate.getShareLinkInfo().getOtherParam().putAll(params.getMap());

        if (params.containsKey("authType") || params.containsKey("authToken")) {
            log.debug("从otherParam中检测到临时认证参数");
            URLParamUtil.addTempAuthParam(parserCreate,
                    params.getString("authType"),
                    params.getString("authToken"),
                    params.getString("authPassword"),
                    params.getString("authInfo1"),
                    params.getString("authInfo2"),
                    params.getString("authInfo3"),
                    params.getString("authInfo4"),
                    params.getString("authInfo5"));
        }

        return applyDonatedAccountFallback(parserCreate, dbService);
    }

    public static void recordDonatedAccountFailureIfNeeded(DbService dbService, JsonObject otherParam,
                                                           Throwable cause) {
        if (!isLikelyAuthFailure(cause) || otherParam == null) {
            return;
        }
        String donatedAccountToken = otherParam.getString("donatedAccountToken");
        if (StringUtils.isBlank(donatedAccountToken)) {
            return;
        }
        dbService.recordDonatedAccountFailureByToken(donatedAccountToken)
                .onFailure(e -> log.warn("记录捐赠账号失败次数失败", e));
    }

    public static void recordAutoDonatedFailureIfNeeded(DbService dbService, ShareLinkInfo shareLinkInfo,
                                                        Throwable cause) {
        if (shareLinkInfo == null || !isLikelyAuthFailure(cause)) {
            return;
        }
        Object tokenObj = shareLinkInfo.getOtherParam().get(DONATED_ACCOUNT_TOKEN);
        if (!(tokenObj instanceof String) || StringUtils.isBlank((String) tokenObj)) {
            return;
        }
        dbService.recordDonatedAccountFailureByToken((String) tokenObj)
                .onFailure(e -> log.warn("记录自动捐赠账号失败次数失败", e));
    }

    private static Future<Void> applyDonatedAccountFallback(ParserCreate parserCreate, DbService dbService) {
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
        Map<String, Object> otherParam = shareLinkInfo.getOtherParam();
        if (Boolean.TRUE.equals(otherParam.get(TEMP_AUTH_ADDED))) {
            return Future.succeededFuture();
        }
        String type = shareLinkInfo.getType();
        if (StringUtils.isBlank(type) || hasUsableStaticAuthConfig(type)) {
            return Future.succeededFuture();
        }
        return dbService.getRandomDonatedAccount(type.toUpperCase())
                .compose(res -> {
                    if (!Integer.valueOf(200).equals(res.getInteger("code"))) {
                        return Future.succeededFuture();
                    }
                    JsonObject data = res.getJsonObject("data");
                    if (data == null || data.isEmpty()) {
                        return Future.succeededFuture();
                    }
                    String username = data.getString("username");
                    String password = data.getString("password");
                    String token = data.getString("token");
                    if (StringUtils.isBlank(username) && StringUtils.isBlank(password) && StringUtils.isBlank(token)) {
                        return Future.succeededFuture();
                    }
                    MultiMap tempAuth = MultiMap.caseInsensitiveMultiMap();
                    if (StringUtils.isNotBlank(username)) {
                        tempAuth.set("username", username);
                    }
                    if (StringUtils.isNotBlank(password)) {
                        tempAuth.set("password", password);
                    }
                    if (StringUtils.isNotBlank(token)) {
                        tempAuth.set("token", token);
                    }
                    otherParam.put(ConfigConstant.AUTHS, tempAuth);
                    otherParam.put(TEMP_AUTH_ADDED, true);
                    String donatedAccountToken = data.getString("donatedAccountToken");
                    if (StringUtils.isNotBlank(donatedAccountToken)) {
                        otherParam.put(DONATED_ACCOUNT_TOKEN, donatedAccountToken);
                    }
                    log.debug("已自动应用捐赠账号: type={}", type);
                    return Future.<Void>succeededFuture();
                })
                .recover(err -> {
                    log.warn("自动获取捐赠账号失败: type={}", type, err);
                    return Future.succeededFuture();
                });
    }

    private static boolean hasUsableStaticAuthConfig(String type) {
        LocalMap<Object, Object> localMap = VertxHolder.getVertxInstance().sharedData()
                .getLocalMap(ConfigConstant.LOCAL);
        if (!localMap.containsKey(ConfigConstant.AUTHS)) {
            return false;
        }
        JsonObject auths = (JsonObject) localMap.get(ConfigConstant.AUTHS);
        JsonObject cfg = auths.getJsonObject(type);
        if (cfg == null) {
            return false;
        }
        for (String key : cfg.fieldNames()) {
            Object value = cfg.getValue(key);
            if (value != null && StringUtils.isNotBlank(value.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLikelyAuthFailure(Throwable cause) {
        if (cause == null) {
            return false;
        }
        String msg = cause.getMessage();
        if (StringUtils.isBlank(msg)) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains("auth")
                || lower.contains("token")
                || lower.contains("cookie")
                || lower.contains("password")
                || lower.contains("credential")
                || lower.contains("401")
                || lower.contains("403")
                || lower.contains("unauthorized")
                || lower.contains("forbidden")
                || lower.contains("expired")
                || lower.contains("登录")
                || lower.contains("认证");
    }
}