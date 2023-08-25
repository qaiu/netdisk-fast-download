package cn.qaiu.lz.web.service;

import cn.qaiu.lz.common.model.UserInfo;
import cn.qaiu.lz.web.model.StatisticsInfo;
import cn.qaiu.vx.core.base.BaseAsyncService;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * lz-web
 * <br>Create date 2021/7/12 17:16
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@ProxyGen
public interface DbService extends BaseAsyncService {
    Future<JsonObject> sayOk(String data);
    Future<JsonObject> sayOk2(String data, UserInfo holder);

    Future<StatisticsInfo> getStatisticsInfo();
}
