package cn.qaiu.vx.core.base;

import cn.qaiu.vx.core.annotaions.HandleSortFilter;
import cn.qaiu.vx.core.interceptor.AfterInterceptor;
import cn.qaiu.vx.core.model.JsonResult;
import cn.qaiu.vx.core.util.ReflectionUtil;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    Logger LOGGER = LoggerFactory.getLogger(BaseHttpApi.class);

    default void doFireJsonObjectResponse(RoutingContext ctx, JsonObject jsonObject) {
        if (!isResponseDone(ctx)) {
            fireJsonObjectResponse(ctx, jsonObject);
        }
        handleAfterInterceptor(ctx, jsonObject);
    }


    default <T> void doFireJsonResultResponse(RoutingContext ctx, JsonResult<T> jsonResult) {
        if (!isResponseDone(ctx)) {
            fireJsonResultResponse(ctx, jsonResult);
        }
        handleAfterInterceptor(ctx, jsonResult.toJsonObject());
    }

    default void doFireJsonObjectResponse(RoutingContext ctx, JsonObject jsonObject, int statusCode) {
        if (!isResponseDone(ctx)) {
            fireJsonObjectResponse(ctx, jsonObject, statusCode);
        }
        handleAfterInterceptor(ctx, jsonObject);
    }


    default <T> void doFireJsonResultResponse(RoutingContext ctx, JsonResult<T> jsonResult, int statusCode) {
        if (!isResponseDone(ctx)) {
            fireJsonResultResponse(ctx, jsonResult, statusCode);
        }
        handleAfterInterceptor(ctx, jsonResult.toJsonObject());
    }

    default Set<AfterInterceptor> getAfterInterceptor() {
        return AfterInterceptorHolder.INSTANCES;
    }

    class AfterInterceptorHolder {
        private static final Set<AfterInterceptor> INSTANCES = loadAfterInterceptors();

        private static Set<AfterInterceptor> loadAfterInterceptors() {
            Set<Class<? extends AfterInterceptor>> afterInterceptorClassSet =
                    reflections.getSubTypesOf(AfterInterceptor.class);
            if (afterInterceptorClassSet == null || afterInterceptorClassSet.isEmpty()) {
                return Collections.emptySet();
            }
            return afterInterceptorClassSet.stream()
                    .filter(AfterInterceptorHolder::isEnabled)
                    .sorted(AfterInterceptorHolder::compareOrder)
                    .map(AfterInterceptorHolder::newInterceptor)
                    .filter(Objects::nonNull)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toCollection(LinkedHashSet::new),
                            Collections::unmodifiableSet));
        }

        private static boolean isEnabled(Class<? extends AfterInterceptor> clazz) {
            HandleSortFilter sort = clazz.getAnnotation(HandleSortFilter.class);
            return sort == null || sort.value() >= 0;
        }

        private static int compareOrder(Class<? extends AfterInterceptor> left, Class<? extends AfterInterceptor> right) {
            return Integer.compare(order(left), order(right));
        }

        private static int order(Class<? extends AfterInterceptor> clazz) {
            HandleSortFilter sort = clazz.getAnnotation(HandleSortFilter.class);
            return sort == null ? 0 : sort.value();
        }

        private static AfterInterceptor newInterceptor(Class<? extends AfterInterceptor> clazz) {
            try {
                return ReflectionUtil.newWithNoParam(clazz);
            } catch (Exception e) {
                LOGGER.warn("AfterInterceptor 初始化失败，已跳过: {}", clazz.getName(), e);
                return null;
            }
        }
    }

    default void handleAfterInterceptor(RoutingContext ctx, JsonObject jsonObject) {
        if (ctx.response().closed()) {
            return;
        }
        Set<AfterInterceptor> afterInterceptor = getAfterInterceptor();
        afterInterceptor.forEach(ai -> {
            try {
                ai.handle(ctx, jsonObject);
            } catch (Exception e) {
                LOGGER.warn("AfterInterceptor 执行失败: {}", ai.getClass().getName(), e);
            }
        });
        if (!isResponseDone(ctx)) {
            fireTextResponse(ctx, "handleAfterInterceptor: response not end");
        }
    }

    default boolean isResponseDone(RoutingContext ctx) {
        return ctx.response().ended() || ctx.response().closed();
    }

}
