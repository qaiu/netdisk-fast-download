package cn.qaiu.lz.common.interceptorImpl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RateLimiter {

    private static final Map<String, RequestInfo> ipRequestMap = new ConcurrentHashMap<>();
    private static int MAX_REQUESTS = 10; // 最大请求次数
    private static long TIME_WINDOW = 60 * 1000; // 时间窗口（毫秒）

    private static String PATH_REG; // 限流路径正则

    public static void init(JsonObject rateLimitConfig) {
        MAX_REQUESTS = rateLimitConfig.getInteger("limit", 10);
        TIME_WINDOW = rateLimitConfig.getInteger("timeWindow", 60) * 1000L; // 转换为毫秒
        PATH_REG = rateLimitConfig.getString("pathReg", "/.*");
        log.info("RateLimiter initialized with max requests: {}, time window: {} ms, path regex: {}",
                MAX_REQUESTS, TIME_WINDOW, PATH_REG);
    }

    synchronized public static Future<Void> checkRateLimit(HttpServerRequest request) {
        Promise<Void> promise = Promise.promise();
        if (!request.path().matches(PATH_REG)) {
            // 如果请求路径不匹配正则，则不进行限流
            promise.complete();
            return promise.future();
        }

        String ip = request.remoteAddress().host();

        ipRequestMap.compute(ip, (key, requestInfo) -> {
            long currentTime = System.currentTimeMillis();
            if (requestInfo == null || currentTime - requestInfo.timestamp > TIME_WINDOW) {
                // 初始化或重置计数器
                return new RequestInfo(1, currentTime);
            } else {
                // 增加计数器
                requestInfo.count++;
                return requestInfo;
            }
        });

        RequestInfo info = ipRequestMap.get(ip);
        if (info.count > MAX_REQUESTS) {
            // 超过限制
            // 计算剩余时间
            long remainingTime = TIME_WINDOW - (System.currentTimeMillis() - info.timestamp);
            BigDecimal bigDecimal = BigDecimal.valueOf(remainingTime / 1000.0)
                    .setScale(2, RoundingMode.HALF_UP);
            promise.fail("请求次数太多了，请" + bigDecimal + "秒后再试。");
        } else {
            // 未超过限制，继续处理
            promise.complete();
        }
        return promise.future();
    }

    private static class RequestInfo {
        int count;
        long timestamp;

        RequestInfo(int count, long time) {
            this.count = count;
            this.timestamp = time;
        }
    }
}
