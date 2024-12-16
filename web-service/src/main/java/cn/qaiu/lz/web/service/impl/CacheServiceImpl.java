package cn.qaiu.lz.web.service.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.lz.common.cache.CacheConfigLoader;
import cn.qaiu.lz.common.cache.CacheManager;
import cn.qaiu.lz.common.cache.CacheTotalField;
import cn.qaiu.lz.web.model.CacheLinkInfo;
import cn.qaiu.lz.web.service.CacheService;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.vx.core.annotaions.Service;
import cn.qaiu.vx.core.util.ConfigConstant;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.Date;

@Service
public class CacheServiceImpl implements CacheService {

    private final CacheManager cacheManager = new CacheManager();

    private Future<CacheLinkInfo> getAndSaveCachedShareLink(ParserCreate parserCreate) {
        LocalMap<Object, Object> localMap = VertxHolder.getVertxInstance().sharedData()
                .getLocalMap(ConfigConstant.LOCAL);
        if (localMap.containsKey(ConfigConstant.PROXY)) {
            JsonObject proxy = (JsonObject) localMap.get(ConfigConstant.PROXY);
            String type = parserCreate.getShareLinkInfo().getType();
            if (proxy.containsKey(type)) {
                parserCreate.getShareLinkInfo().getOtherParam().put(ConfigConstant.PROXY, proxy.getJsonObject(type));
            }
        }

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
                parserCreate.createTool().parse().onSuccess(redirectUrl -> {
                    long expires = System.currentTimeMillis() +
                            CacheConfigLoader.getDuration(shareLinkInfo.getType()) * 60 * 1000L;
                    result.setDirectLink(redirectUrl);
                    // result.setExpires(generateDate(expires));
                    promise.complete(result);
                    // 更新缓存
                    // 将直链存储到缓存
                    CacheLinkInfo cacheLinkInfo = new CacheLinkInfo(JsonObject.of(
                            "directLink", redirectUrl,
                            "expiration", expires,
                            "shareKey", cacheKey
                    ));
                    cacheManager.cacheShareLink(cacheLinkInfo).onFailure(Throwable::printStackTrace);
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
