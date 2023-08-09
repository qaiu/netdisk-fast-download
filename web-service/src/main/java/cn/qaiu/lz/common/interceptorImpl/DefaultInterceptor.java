package cn.qaiu.lz.common.interceptorImpl;

import cn.qaiu.vx.core.base.BaseHttpApi;
import cn.qaiu.vx.core.interceptor.Interceptor;
import cn.qaiu.vx.core.util.SharedDataUtil;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认拦截器实现
 * 校验用户是否合法 <br>
 * TODO 暂时只做简单实现
 */
@Slf4j
public class DefaultInterceptor implements Interceptor, BaseHttpApi {

    private final JsonArray ignores = SharedDataUtil.getJsonArrayForCustomConfig("ignoresReg");

    @Override
    public void beforeHandle(RoutingContext ctx) {
        ctx.next();
    }

    @Override
    public void afterHandle(RoutingContext context) {

    }
}
