package cn.qaiu.lz.common.interceptorImpl;

import cn.qaiu.vx.core.annotaions.HandleSortFilter;
import cn.qaiu.vx.core.interceptor.AfterInterceptor;
import cn.qaiu.vx.core.util.SharedDataUtil;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

import static cn.qaiu.vx.core.util.ConfigConstant.IGNORES_REG;

/**
 *
 */
@Slf4j
@HandleSortFilter(99)
public class LogStatistics implements AfterInterceptor {

    protected final JsonArray ignores = SharedDataUtil.getJsonArrayForCustomConfig(IGNORES_REG);

    @Override
    public void handle(HttpServerRequest request, JsonObject responseData) {
        System.out.println("后置拦截-->" + responseData + " path:" + request.path());
    }
}
