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
import cn.qaiu.vx.core.annotaions.Service;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.Date;

@Service
@Slf4j
public class CacheServiceImpl implements CacheService {

    private final CacheManager cacheManager = new CacheManager();

    private Future<CacheLinkInfo> getAndSaveCachedShareLink(ParserCreate parserCreate) {

        URLParamUtil.addParam(parserCreate);

        Promise<CacheLinkInfo> promise = Promise.promise();
        // 构建组合的缓存key
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
        // 尝试从缓存中获取
        String cacheKey = shareLinkInfo.getCacheKey();
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
                    long expires = System.currentTimeMillis() +
                            CacheConfigLoader.getDuration(shareLinkInfo.getType()) * 60 * 1000L;
                    result.setDirectLink(redirectUrl);

                    CacheLinkInfo cacheLinkInfo = new CacheLinkInfo(JsonObject.of(
                            "directLink", redirectUrl,
                            "expiration", expires,
                            "shareKey", cacheKey
                    ));
                    if (shareLinkInfo.getOtherParam().containsKey("fileInfo")) {
                        try {
                            FileInfo fileInfo = (FileInfo) shareLinkInfo.getOtherParam().get("fileInfo");
                            result.setFileInfo(fileInfo);
                            cacheLinkInfo.setFileInfo(fileInfo);
                        } catch (Exception ignored) {
                            log.error("文件对象转换异常");
                        }
                    }
                    promise.complete(result);
                    // 更新缓存
                    // 将直链存储到缓存
                    cacheManager.cacheShareLink(cacheLinkInfo);
                    cacheManager.updateTotalByField(cacheKey, CacheTotalField.API_PARSER_TOTAL).onFailure(Throwable::printStackTrace);
                }).onFailure(promise::fail);
            } else {
                result.setExpires(generateDate(result.getExpiration()));
                promise.complete(result);
                cacheManager.updateTotalByField(cacheKey, CacheTotalField.CACHE_HIT_TOTAL).onFailure(Throwable::printStackTrace);
            }
        }).onFailure(t -> promise.fail(t.fillInStackTrace()));
        return promise.future();
    }

    private String generateDate(Long ts) {
        return DateFormatUtils.format(new Date(ts), "yyyy-MM-dd HH:mm:ss");
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
        return getAndSaveCachedShareLink(parserCreate);
    }
}
