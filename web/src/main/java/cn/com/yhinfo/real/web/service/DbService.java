package cn.com.yhinfo.real.web.service;

import cn.com.yhinfo.core.base.BaseAsyncService;
import cn.com.yhinfo.real.common.model.UserInfo;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * sinoreal2-web
 * <br>Create date 2021/7/12 17:16
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@ProxyGen
public interface DbService extends BaseAsyncService {
    Future<JsonObject> sayOk(String data);
    Future<JsonObject> sayOk2(String data, UserInfo holder);
}
