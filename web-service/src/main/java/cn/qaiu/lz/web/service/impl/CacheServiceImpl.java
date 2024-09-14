package cn.qaiu.lz.web.service.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.lz.common.cache.CacheConfigLoader;
import cn.qaiu.lz.common.cache.CacheManager;
import cn.qaiu.lz.web.model.CacheLinkInfo;
import cn.qaiu.lz.web.service.CacheService;
import cn.qaiu.parser.PanDomainTemplate;
import cn.qaiu.vx.core.annotaions.Service;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.Date;

@Service
public class CacheServiceImpl implements CacheService {

    private final CacheManager cacheManager = new CacheManager();

    @Override
    public Future<CacheLinkInfo> getAndSaveCachedShareLink(PanDomainTemplate template) {
        Promise<CacheLinkInfo> promise = Promise.promise();

        // 构建组合的缓存key
        ShareLinkInfo shareLinkInfo = template.getShareLinkInfo();
        String cacheKey = generateCacheKey(shareLinkInfo.getType(), shareLinkInfo.getShareKey());
        // 尝试从缓存中获取
        cacheManager.get(cacheKey).onSuccess(result -> {
            // 判断是否已过期
            // 未命中或者过期
            if (!result.getCacheHit() || result.getExpiration() < System.currentTimeMillis()) {
                template.createTool().parse().onSuccess(redirectUrl -> {
                    long expires = System.currentTimeMillis() +
                            CacheConfigLoader.getDuration(shareLinkInfo.getType()) * 60 * 1000;
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
                }).onFailure(promise::fail);
            } else {
                result.setExpires(generateDate(result.getExpiration()));
                promise.complete(result);
            }
        }).onFailure(t -> promise.fail(t.fillInStackTrace()));
        return promise.future();
    }

    private String generateCacheKey(String type, String shareKey) {
        // 将type和shareKey组合成一个字符串作为缓存key
        return type + ":" + shareKey;
    }

    private String generateDate(Long ts) {
        return DateFormatUtils.format(new Date(ts), "yyyy-MM-dd hh:mm:ss");
    }
}
