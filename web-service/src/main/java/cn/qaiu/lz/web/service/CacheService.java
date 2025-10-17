package cn.qaiu.lz.web.service;

import cn.qaiu.lz.web.model.CacheLinkInfo;
import cn.qaiu.vx.core.base.BaseAsyncService;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2024/9/12 8:26
 */
@ProxyGen
public interface CacheService extends BaseAsyncService {

    Future<CacheLinkInfo> getCachedByShareKeyAndPwd(String type, String shareKey, String pwd, JsonObject otherParam);

    Future<CacheLinkInfo> getCachedByShareUrlAndPwd(String shareUrl, String pwd, JsonObject otherParam);
}
