package cn.qaiu.vx.core.base;

import cn.qaiu.vx.core.interceptor.AfterInterceptor;
import cn.qaiu.vx.core.model.JsonResult;
import cn.qaiu.vx.core.util.CommonUtil;
import cn.qaiu.vx.core.util.ReflectionUtil;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.reflections.Reflections;

import java.util.Set;

import static cn.qaiu.vx.core.util.ResponseUtil.*;

/**
 * 统一响应处理
 * <br>Create date 2021-05-06 09:20:37
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public interface BaseHttpApi {

    // 需要扫描注册的Router路径
    Reflections reflections = ReflectionUtil.getReflections();

    default void doFireJsonObjectResponse(RoutingContext ctx, JsonObject jsonObject) {
        if (!ctx.response().ended()) {
            fireJsonObjectResponse(ctx, jsonObject);
        }
        handleAfterInterceptor(ctx, jsonObject);
    }


    default <T> void doFireJsonResultResponse(RoutingContext ctx, JsonResult<T> jsonResult) {
        if (!ctx.response().ended()) {
            fireJsonResultResponse(ctx, jsonResult);
        }
        handleAfterInterceptor(ctx, jsonResult.toJsonObject());
    }

    default void doFireJsonObjectResponse(RoutingContext ctx, JsonObject jsonObject, int statusCode) {
        if (!ctx.response().ended()) {
            fireJsonObjectResponse(ctx, jsonObject, statusCode);
        }
        handleAfterInterceptor(ctx, jsonObject);
    }


    default <T> void doFireJsonResultResponse(RoutingContext ctx, JsonResult<T> jsonResult, int statusCode) {
        if (!ctx.response().ended()) {
            fireJsonResultResponse(ctx, jsonResult, statusCode);
        }
        handleAfterInterceptor(ctx, jsonResult.toJsonObject());
    }

    default Set<AfterInterceptor> getAfterInterceptor() {

        Set<Class<? extends AfterInterceptor>> afterInterceptorClassSet =
                reflections.getSubTypesOf(AfterInterceptor.class);
        if (afterInterceptorClassSet == null) {
            return null;
        }
        return CommonUtil.sortClassSet(afterInterceptorClassSet);
    }

    default void handleAfterInterceptor(RoutingContext ctx, JsonObject jsonObject) {
        Set<AfterInterceptor> afterInterceptor = getAfterInterceptor();
        if (afterInterceptor != null) {
            afterInterceptor.forEach(ai -> ai.handle(ctx, jsonObject));
        }
        if (!ctx.response().ended()) {
            fireTextResponse(ctx, "handleAfterInterceptor: response not end");
        }
    }

}
