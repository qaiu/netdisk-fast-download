package cn.qaiu.lz.web.service;

import cn.qaiu.lz.web.model.CacheLinkInfo;
import cn.qaiu.parser.PanDomainTemplate;
import cn.qaiu.vx.core.base.BaseAsyncService;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * @date 2024/9/12 8:26
 */
@ProxyGen
public interface CacheService extends BaseAsyncService {

    Future<CacheLinkInfo> getAndSaveCachedShareLink(PanDomainTemplate shareLinkInfo);
}
