package cn.qaiu.lz.web.service.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.lz.common.cache.CacheConfigLoader;
import cn.qaiu.lz.common.cache.CacheManager;
import cn.qaiu.lz.common.cache.CacheTotalField;
import cn.qaiu.lz.common.util.URLParamUtil;
import cn.qaiu.lz.web.model.CacheLinkInfo;
import cn.qaiu.lz.web.service.CacheService;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.parser.clientlink.ClientLinkGeneratorFactory;
import cn.qaiu.parser.clientlink.ClientLinkType;
import cn.qaiu.vx.core.annotaions.Service;
import cn.qaiu.vx.core.util.AsyncServiceUtil;
import cn.qaiu.vx.core.util.ConfigConstant;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CacheServiceImpl implements CacheService {

    private static final String SKIP_CLIENT_LINKS = "_skipClientLinks";
    private static final String TEMP_AUTH_ADDED = "__TEMP_AUTH_ADDED";
    private static final String DONATED_ACCOUNT_TOKEN = "__AUTO_DONATED_ACCOUNT_TOKEN";

    private final CacheManager cacheManager = new CacheManager();
    private final DbService dbService = AsyncServiceUtil.getAsyncServiceInstance(DbService.class);

    static {
        // 服务类加载时注册缓存定时清理任务
        CacheManager.registerPeriodicCleanup();
    }

    private Future<CacheLinkInfo> getAndSaveCachedShareLink(ParserCreate parserCreate) {

        // 认证、域名相关（检查是否已经添加过参数，避免重复调用）
        if (!parserCreate.getShareLinkInfo().getOtherParam().containsKey("__PARAMS_ADDED")) {
            URLParamUtil.addParam(parserCreate);
            parserCreate.getShareLinkInfo().getOtherParam().put("__PARAMS_ADDED", true);
        }

        Promise<CacheLinkInfo> promise = Promise.promise();
        // 构建组合的缓存key
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
        String cacheKey = shareLinkInfo.getCacheKey();
        
        // 使用配置文件中的默认缓存时长
        final int effectiveCacheDuration = CacheConfigLoader.getDuration(shareLinkInfo.getType());

        // 尝试从缓存中获取
        cacheManager.get(cacheKey).onSuccess(result -> {
            // 判断是否已过期
            // 未命中或者过期
            if (!result.getCacheHit() || result.getExpiration() < System.currentTimeMillis()) {
                // parse
                result.setCacheHit(false);
                result.setExpiration(0L);
                IPanTool tool;
                try {
                    tool = parserCreate.createTool();
                } catch (Exception e) {
                    Throwable cause = e;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    promise.fail(cause);
                    return;
                }
                IPanTool.closeAfter(tool, tool::parse).onFailure(err -> {
                    recordAutoDonatedFailureIfNeeded(shareLinkInfo, err);
                }).onSuccess(redirectUrl -> {
                    // 使用 effectiveCacheDuration
                    long expires = System.currentTimeMillis() + effectiveCacheDuration * 60 * 1000L;
                    result.setDirectLink(redirectUrl);
                    // 设置返回结果的过期时间
                    result.setExpiration(expires);
                    result.setExpires(generateDate(expires));
                    
                    // 调试日志：检查解析器返回的otherParam
                    log.debug("[解析完成] shareKey={}, otherParam.keys={}, hasFileInfo={}",
                            cacheKey, 
                            shareLinkInfo.getOtherParam().keySet(),
                            shareLinkInfo.getOtherParam().containsKey("fileInfo"));

                    CacheLinkInfo cacheLinkInfo = new CacheLinkInfo(JsonObject.of(
                            "directLink", redirectUrl,
                            "expiration", expires,
                            "shareKey", cacheKey
                    ));
                    // 提取并设置文件信息
                    if (shareLinkInfo.getOtherParam().containsKey("fileInfo")) {
                        try {
                            FileInfo fileInfo = (FileInfo) shareLinkInfo.getOtherParam().get("fileInfo");
                            result.setFileInfo(fileInfo);
                            cacheLinkInfo.setFileInfo(fileInfo);
                            log.debug("[设置文件信息] shareKey={}, fileName={}, size={}",
                                    cacheKey, fileInfo.getFileName(), fileInfo.getSize());
                        } catch (Exception e) {
                            log.error("文件对象转换异常: shareKey={}", cacheKey, e);
                        }
                    } else {
                        log.debug("[文件信息缺失] 解析器未返回fileInfo: shareKey={}, otherParam.keys={}",
                                cacheKey, shareLinkInfo.getOtherParam().keySet());
                    }
                    if (shouldGenerateClientLinks(shareLinkInfo)) {
                        // 传递 downloadHeaders 并生成下载命令
                        processDownloadHeaders(shareLinkInfo, cacheLinkInfo, result);
                    }
                    promise.complete(result);
                    // 更新缓存
                    cacheManager.cacheShareLink(cacheLinkInfo);
                    cacheManager.updateTotalByField(cacheKey, CacheTotalField.API_PARSER_TOTAL).onFailure(e -> log.error("更新API解析计数失败: cacheKey={}", cacheKey, e));
                }).onFailure(promise::fail);
            } else {
                // 缓存命中，生成过期时间并生成下载命令
                result.setExpires(generateDate(result.getExpiration()));
                
                if (shouldGenerateClientLinks(shareLinkInfo)) {
                    // 初始化 otherParam（如果为空）
                    if (result.getOtherParam() == null) {
                        result.setOtherParam(new HashMap<>());
                    }

                    // 生成下载命令（aria2、curl）
                    generateDownloadCommands(result);
                }
                
                promise.complete(result);
                cacheManager.updateTotalByField(cacheKey, CacheTotalField.CACHE_HIT_TOTAL)
                        .onFailure(e -> log.error("更新缓存命中计数失败: cacheKey={}", cacheKey, e));
            }
        }).onFailure(promise::tryFail);

        return promise.future();
    }

    private String generateDate(Long ts) {
        return DateFormatUtils.format(new Date(ts), "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 当本次请求没有携带临时认证参数（auth=xxx），且该网盘类型在 app-dev.yml 中也没有配置可用的
     * 静态账号密码/token 时，自动从捐赠账号池中随机挑选一个可用账号使用，
     * 这样捐赠者捐赠账号后，普通请求（不带 auth 参数）也能直接受益，无需前端额外拼接参数。
     */
    private Future<Void> applyDonatedAccountFallback(ParserCreate parserCreate) {
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
        Map<String, Object> otherParam = shareLinkInfo.getOtherParam();
        if (Boolean.TRUE.equals(otherParam.get(TEMP_AUTH_ADDED))) {
            // 请求已经携带了个人配置/捐赠账号的临时认证参数，无需再自动回退
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

    /**
     * 检查 app-dev.yml 中该网盘类型是否已配置了可用的静态认证信息（非空的 username/password/token/authorization 等）。
     * 如果已配置，则不需要再自动回退到捐赠账号池。
     */
    private boolean hasUsableStaticAuthConfig(String type) {
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

    /**
     * 自动回退使用的捐赠账号解析失败时，记录一次失败次数（达到阈值后台账号会被自动禁用）。
     */
    private void recordAutoDonatedFailureIfNeeded(ShareLinkInfo shareLinkInfo, Throwable cause) {
        Object tokenObj = shareLinkInfo.getOtherParam().get(DONATED_ACCOUNT_TOKEN);
        if (!(tokenObj instanceof String) || StringUtils.isBlank((String) tokenObj)) {
            return;
        }
        if (!isLikelyAuthFailure(cause)) {
            return;
        }
        dbService.recordDonatedAccountFailureByToken((String) tokenObj)
                .onFailure(e -> log.warn("记录自动捐赠账号失败次数失败", e));
    }

    private boolean isLikelyAuthFailure(Throwable cause) {
        if (cause == null) {
            return false;
        }
        String msg = cause.getMessage();
        if (msg == null || msg.isBlank()) {
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

    private boolean shouldGenerateClientLinks(ShareLinkInfo shareLinkInfo) {
        if (shareLinkInfo == null || shareLinkInfo.getOtherParam() == null) {
            return true;
        }
        return !Boolean.TRUE.equals(shareLinkInfo.getOtherParam().get(SKIP_CLIENT_LINKS));
    }

    /**
     * 处理下载请求头并生成下载命令
     * 从 shareLinkInfo 中提取 downloadHeaders，传递到 cacheLinkInfo 和 result
     */
    private void processDownloadHeaders(ShareLinkInfo shareLinkInfo, CacheLinkInfo cacheLinkInfo, 
                                         CacheLinkInfo result) {
        try {
            // 提取 downloadHeaders（如果不存在，使用空Map）
            Map<String, String> downloadHeaders = new HashMap<>();
            
            if (shareLinkInfo.getOtherParam() != null 
                    && shareLinkInfo.getOtherParam().containsKey("downloadHeaders")) {
                @SuppressWarnings("unchecked")
                Map<String, String> headers = (Map<String, String>) shareLinkInfo.getOtherParam().get("downloadHeaders");
                if (headers != null) {
                    downloadHeaders = headers;
                    log.debug("从shareLinkInfo提取downloadHeaders: shareKey={}, 请求头数量={}",
                            cacheLinkInfo.getShareKey(), downloadHeaders.size());
                }
            }
            
            // 初始化 otherParam
            if (cacheLinkInfo.getOtherParam() == null) {
                cacheLinkInfo.setOtherParam(new HashMap<>());
            }
            if (result.getOtherParam() == null) {
                result.setOtherParam(new HashMap<>());
            }
            
            // 传递 downloadHeaders 到两个对象
            cacheLinkInfo.getOtherParam().put("downloadHeaders", downloadHeaders);
            result.getOtherParam().put("downloadHeaders", downloadHeaders);
            
            // 使用已有的工具类生成下载命令
            generateCommandsFromShareLinkInfo(shareLinkInfo, cacheLinkInfo, result);
            
        } catch (Exception e) {
            log.error("处理下载请求头异常: shareKey={}", cacheLinkInfo.getShareKey(), e);
        }
    }
    
    /**
     * 使用 ClientLinkGeneratorFactory 生成下载命令
     */
    private void generateCommandsFromShareLinkInfo(ShareLinkInfo shareLinkInfo, 
                                                    CacheLinkInfo cacheLinkInfo, 
                                                    CacheLinkInfo result) {
        try {
            // 使用已有的 ClientLinkGeneratorFactory 生成命令
            Map<ClientLinkType, String> clientLinks = ClientLinkGeneratorFactory.generateAll(shareLinkInfo);
            
            // 提取各命令并存储
            String curlCommand = clientLinks.get(ClientLinkType.CURL);
            String aria2Command = clientLinks.get(ClientLinkType.ARIA2);
            String thunderLink = clientLinks.get(ClientLinkType.THUNDER);
            
            // 设置命令到 cacheLinkInfo 和 result
            if (curlCommand != null) {
                cacheLinkInfo.getOtherParam().put("curlCommand", curlCommand);
                result.getOtherParam().put("curlCommand", curlCommand);
            }
            if (aria2Command != null) {
                cacheLinkInfo.getOtherParam().put("aria2Command", aria2Command);
                result.getOtherParam().put("aria2Command", aria2Command);
            }
            if (thunderLink != null) {
                cacheLinkInfo.getOtherParam().put("thunderLink", thunderLink);
                result.getOtherParam().put("thunderLink", thunderLink);
            }
            
            log.debug("已生成下载命令: shareKey={}, commands={}", 
                    cacheLinkInfo.getShareKey(), clientLinks.keySet());
        } catch (Exception e) {
            log.error("生成下载命令异常: shareKey={}", cacheLinkInfo.getShareKey(), e);
        }
    }

    /**
     * 生成下载命令（缓存命中时）
     */
    private void generateDownloadCommands(CacheLinkInfo cacheLinkInfo) {
        if (cacheLinkInfo.getDirectLink() == null || cacheLinkInfo.getDirectLink().isEmpty()) {
            return;
        }

        try {
            // 构建临时 ShareLinkInfo 用于生成命令
            ShareLinkInfo tempInfo = ShareLinkInfo.newBuilder()
                    .shareUrl(cacheLinkInfo.getDirectLink())
                    .build();
            tempInfo.getOtherParam().put("downloadUrl", cacheLinkInfo.getDirectLink());
            
            // 复制 downloadHeaders
            if (cacheLinkInfo.getOtherParam() != null 
                    && cacheLinkInfo.getOtherParam().containsKey("downloadHeaders")) {
                tempInfo.getOtherParam().put("downloadHeaders", cacheLinkInfo.getOtherParam().get("downloadHeaders"));
            }
            
            // 复制文件信息
            if (cacheLinkInfo.getFileInfo() != null) {
                tempInfo.getOtherParam().put("fileInfo", cacheLinkInfo.getFileInfo());
            }
            
            // 使用 ClientLinkGeneratorFactory 生成命令
            Map<ClientLinkType, String> clientLinks = ClientLinkGeneratorFactory.generateAll(tempInfo);
            
            // 存储命令
            if (clientLinks.containsKey(ClientLinkType.CURL)) {
                cacheLinkInfo.getOtherParam().put("curlCommand", clientLinks.get(ClientLinkType.CURL));
            }
            if (clientLinks.containsKey(ClientLinkType.ARIA2)) {
                cacheLinkInfo.getOtherParam().put("aria2Command", clientLinks.get(ClientLinkType.ARIA2));
            }
            if (clientLinks.containsKey(ClientLinkType.THUNDER)) {
                cacheLinkInfo.getOtherParam().put("thunderLink", clientLinks.get(ClientLinkType.THUNDER));
            }
            
        } catch (Exception e) {
            log.error("生成下载命令异常: shareKey={}", cacheLinkInfo.getShareKey(), e);
        }
    }

    @Override
    public Future<CacheLinkInfo> getCachedByShareKeyAndPwd(String type, String shareKey, String pwd, JsonObject otherParam) {
        ParserCreate parserCreate;
        try {
            parserCreate = ParserCreate.fromType(type).shareKey(shareKey).setShareLinkInfoPwd(pwd);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
        parserCreate.getShareLinkInfo().getOtherParam().putAll(otherParam.getMap());

        ParserCreate finalParserCreate = parserCreate;
        return applyDonatedAccountFallback(finalParserCreate)
                .compose(v -> getAndSaveCachedShareLink(finalParserCreate));
    }

    @Override
    public Future<CacheLinkInfo> getCachedByShareUrlAndPwd(String shareUrl, String pwd, JsonObject otherParam) {
        ParserCreate parserCreate;
        try {
            parserCreate = ParserCreate.fromShareUrl(shareUrl).setShareLinkInfoPwd(pwd);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
        parserCreate.getShareLinkInfo().getOtherParam().putAll(otherParam.getMap());
        
        // 检查是否有临时认证参数
        if (otherParam.containsKey("authType") || otherParam.containsKey("authToken")) {
            log.debug("从otherParam中检测到临时认证参数");
            URLParamUtil.addTempAuthParam(parserCreate, 
                otherParam.getString("authType"),
                otherParam.getString("authToken"),
                otherParam.getString("authPassword"),
                otherParam.getString("authInfo1"),
                otherParam.getString("authInfo2"),
                otherParam.getString("authInfo3"),
                otherParam.getString("authInfo4"),
                otherParam.getString("authInfo5"));
        }

        ParserCreate finalParserCreate = parserCreate;
        return applyDonatedAccountFallback(finalParserCreate)
                .compose(v -> getAndSaveCachedShareLink(finalParserCreate));
    }
}
