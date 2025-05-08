package cn.qaiu.lz.common.interceptorImpl;

import cn.qaiu.vx.core.annotaions.HandleSortFilter;
import cn.qaiu.vx.core.interceptor.BeforeInterceptor;
import cn.qaiu.vx.core.util.ConfigConstant;
import cn.qaiu.vx.core.util.SharedDataUtil;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import static cn.qaiu.vx.core.util.ConfigConstant.IGNORES_REG;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * 前置拦截器实现
 */
@Slf4j
@HandleSortFilter(1)
public class DefaultInterceptor implements BeforeInterceptor {

    protected final JsonArray ignores = SharedDataUtil.getJsonArrayForCustomConfig(IGNORES_REG);

    @Override
    public void handle(RoutingContext ctx) {
        // 读取配置 如果配置了限流 则进行限流
        if (!SharedDataUtil.getJsonConfig(ConfigConstant.GLOBAL_CONFIG).containsKey("rateLimit")) {
            doNext(ctx);
            return;
        }
        JsonObject rateLimit = SharedDataUtil.getJsonConfig(ConfigConstant.GLOBAL_CONFIG)
                .getJsonObject("rateLimit");
        // # 限流配置
        //rateLimit:
        //  # 是否启用限流
        //  enable: true
        //  # 限流的请求数
        //  limit: 1000
        //  # 限流的时间窗口(单位秒)
        //  timeWindow: 60
        if (rateLimit.getBoolean("enable")) {
            // 获取当前请求的路径
            String path = ctx.request().path();
            // 正则匹配路径
            if (ignores.stream().anyMatch(ignore -> path.matches(ignore.toString()))) {
                // 如果匹配到忽略的路径，则不进行限流
                doNext(ctx);
                return;
            }
            RateLimiter.checkRateLimit(ctx.request())
                    .onSuccess(v -> {
                        // 继续执行下一个拦截器
                        doNext(ctx);
                    })
                    .onFailure(t -> {
                        // 限流失败，返回错误响应
                        log.warn("Rate limit exceeded for path: {}", path);
                        ctx.response().putHeader(CONTENT_TYPE, "text/html; charset=utf-8")
                                .setStatusCode(429)
                                .end(t.getMessage());
                    });
        }
    }

}
