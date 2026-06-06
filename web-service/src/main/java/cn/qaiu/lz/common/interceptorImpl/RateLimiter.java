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
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class RateLimiter {

    private static final Map<String, RequestInfo> ipRequestMap = new ConcurrentHashMap<>();
    private static int MAX_REQUESTS = 10; // 最大请求次数
    private static int MAX_ENTRIES = 10_000;
    private static long TIME_WINDOW = 60 * 1000; // 时间窗口（毫秒）

    private static String PATH_REG = "/.*"; // 限流路径正则（默认匹配所有路径）

    // 上次清理时间
    private static volatile long lastCleanupTime = System.currentTimeMillis();
    // 清理间隔（30秒）
    private static final long CLEANUP_INTERVAL = 30 * 1000;

    public static void init(JsonObject rateLimitConfig) {
        MAX_REQUESTS = rateLimitConfig.getInteger("limit", 10);
        MAX_ENTRIES = rateLimitConfig.getInteger("maxEntries", 10_000);
        TIME_WINDOW = rateLimitConfig.getInteger("timeWindow", 60) * 1000L; // 转换为毫秒
        PATH_REG = rateLimitConfig.getString("pathReg", "/.*");
        log.info("RateLimiter initialized with max requests: {}, time window: {} ms, path regex: {}",
                MAX_REQUESTS, TIME_WINDOW, PATH_REG);
    }

    public static Future<Void> checkRateLimit(HttpServerRequest request) {
        Promise<Void> promise = Promise.promise();
        if (!request.path().matches(PATH_REG)) {
            // 如果请求路径不匹配正则，则不进行限流
            promise.complete();
            return promise.future();
        }

        String ip = request.remoteAddress().host();

        // 基于时间间隔的清理策略，避免 Map 无限增长
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL) {
            cleanupExpiredEntries(now, false);
        }
        RequestInfo info;
        synchronized (RateLimiter.class) {
            if (!ipRequestMap.containsKey(ip) && ipRequestMap.size() >= MAX_ENTRIES) {
                cleanupExpiredEntries(now, true);
                if (ipRequestMap.size() >= MAX_ENTRIES) {
                    promise.fail("限流记录过多，请稍后再试。");
                    return promise.future();
                }
            }

            info = ipRequestMap.compute(ip, (key, requestInfo) -> {
                if (requestInfo == null || now - requestInfo.timestamp > TIME_WINDOW) {
                    // 初始化或重置计数器
                    return new RequestInfo(1, now);
                } else {
                    // 增加计数器
                    requestInfo.count.incrementAndGet();
                    return requestInfo;
                }
            });
        }

        if (info.count.get() > MAX_REQUESTS) {
            // 超过限制
            // 计算剩余时间
            long remainingTime = TIME_WINDOW - (now - info.timestamp);
            BigDecimal bigDecimal = BigDecimal.valueOf(remainingTime / 1000.0)
                    .setScale(2, RoundingMode.HALF_UP);
            promise.fail("请求次数太多了，请" + bigDecimal + "秒后再试。");
        } else {
            // 未超过限制，继续处理
            promise.complete();
        }
        return promise.future();
    }

    /**
     * 清理过期的限流条目
     * 使用 synchronized 避免并发清理
     */
    private static synchronized void cleanupExpiredEntries(long now, boolean force) {
        // 双重检查，避免重复清理
        if (!force && now - lastCleanupTime <= CLEANUP_INTERVAL) {
            return;
        }
        lastCleanupTime = now;

        int sizeBefore = ipRequestMap.size();
        if (sizeBefore > 0) {
            ipRequestMap.entrySet().removeIf(entry -> now - entry.getValue().timestamp > TIME_WINDOW);
            int sizeAfter = ipRequestMap.size();
            if (sizeBefore > 100 || sizeAfter != sizeBefore) {
                log.debug("RateLimiter 清理过期条目: {} -> {}", sizeBefore, sizeAfter);
            }
        }
    }

    private static class RequestInfo {
        final AtomicInteger count;
        volatile long timestamp;

        RequestInfo(int count, long time) {
            this.count = new AtomicInteger(count);
            this.timestamp = time;
        }
    }
}
