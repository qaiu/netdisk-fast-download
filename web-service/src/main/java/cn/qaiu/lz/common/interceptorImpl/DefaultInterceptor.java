package cn.qaiu.lz.common.interceptorImpl;

import cn.qaiu.vx.core.annotaions.HandleSortFilter;
import cn.qaiu.vx.core.interceptor.BeforeInterceptor;
import cn.qaiu.vx.core.util.SharedDataUtil;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import static cn.qaiu.vx.core.util.ConfigConstant.IGNORES_REG;

/**
 * 前置拦截器实现
 */
@Slf4j
@HandleSortFilter(1)
public class DefaultInterceptor implements BeforeInterceptor {

    protected final JsonArray ignores = SharedDataUtil.getJsonArrayForCustomConfig(IGNORES_REG);

    @Override
    public void handle(RoutingContext ctx) {
        System.out.println("进入前置拦截器1->" + ctx.request().path());
        doNext(ctx);
    }

}
