package cn.qaiu.lz.web.service.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.lz.common.cache.CacheConfigLoader;
import cn.qaiu.lz.common.cache.CacheManager;
import cn.qaiu.lz.common.cache.CacheTotalField;
import cn.qaiu.lz.common.util.URLParamUtil;
import cn.qaiu.lz.web.model.CacheLinkInfo;
import cn.qaiu.lz.web.service.CacheService;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.parser.clientlink.ClientLinkGeneratorFactory;
import cn.qaiu.parser.clientlink.ClientLinkType;
import cn.qaiu.vx.core.annotaions.Service;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CacheServiceImpl implements CacheService {

    private final CacheManager cacheManager = new CacheManager();

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
                    promise.fail(e.getCause().getCause());
                    return;
                }
                tool.parse().onSuccess(redirectUrl -> {
                    // 使用 effectiveCacheDuration
                    long expires = System.currentTimeMillis() + effectiveCacheDuration * 60 * 1000L;
                    result.setDirectLink(redirectUrl);
                    // 设置返回结果的过期时间
                    result.setExpiration(expires);
                    result.setExpires(generateDate(expires));
                    
                    // 调试日志：检查解析器返回的otherParam
                    log.info("[解析完成] shareKey={}, otherParam.keys={}, hasFileInfo={}", 
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
                            log.info("[设置文件信息] shareKey={}, fileName={}, size={}", 
                                    cacheKey, fileInfo.getFileName(), fileInfo.getSize());
                        } catch (Exception e) {
                            log.error("文件对象转换异常: shareKey={}", cacheKey, e);
                        }
                    } else {
                        log.warn("[文件信息缺失] 解析器未返回fileInfo: shareKey={}, otherParam.keys={}", 
                                cacheKey, shareLinkInfo.getOtherParam().keySet());
                    }
                    // 传递 downloadHeaders 并生成下载命令
                    processDownloadHeaders(shareLinkInfo, cacheLinkInfo, result);
                    promise.complete(result);
                    // 更新缓存
                    cacheManager.cacheShareLink(cacheLinkInfo);
                    cacheManager.updateTotalByField(cacheKey, CacheTotalField.API_PARSER_TOTAL).onFailure(Throwable::printStackTrace);
                }).onFailure(promise::fail);
            } else {
                // 缓存命中，生成过期时间并生成下载命令
                result.setExpires(generateDate(result.getExpiration()));
                
                // 初始化 otherParam（如果为空）
                if (result.getOtherParam() == null) {
                    result.setOtherParam(new HashMap<>());
                }
                
                // 生成下载命令（aria2、curl）
                generateDownloadCommands(result);
                
                promise.complete(result);
                cacheManager.updateTotalByField(cacheKey, CacheTotalField.CACHE_HIT_TOTAL)
                        .onFailure(Throwable::printStackTrace);
            }
        }).onFailure(t -> promise.fail(t.fillInStackTrace()));

        return promise.future();
    }

    private String generateDate(Long ts) {
        return DateFormatUtils.format(new Date(ts), "yyyy-MM-dd HH:mm:ss");
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
                    log.info("从shareLinkInfo提取downloadHeaders: shareKey={}, 请求头数量={}", 
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
        ParserCreate parserCreate = ParserCreate.fromType(type).shareKey(shareKey).setShareLinkInfoPwd(pwd);
        parserCreate.getShareLinkInfo().getOtherParam().putAll(otherParam.getMap());
        return getAndSaveCachedShareLink(parserCreate);
    }

    @Override
    public Future<CacheLinkInfo> getCachedByShareUrlAndPwd(String shareUrl, String pwd, JsonObject otherParam) {
        ParserCreate parserCreate = ParserCreate.fromShareUrl(shareUrl).setShareLinkInfoPwd(pwd);
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
        
        return getAndSaveCachedShareLink(parserCreate);
    }
}
