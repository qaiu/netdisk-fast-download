package cn.qaiu.lz.web.service.impl;

import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.lz.web.service.ShoutService;
import cn.qaiu.vx.core.annotaions.Service;
import cn.qaiu.vx.core.util.VertxHolder;
import cn.qaiu.vx.core.model.JsonResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@SuppressWarnings("SqlResolve") // 这里是为了避免检查SQL语句的警告
public class ShoutServiceImpl implements ShoutService {
    private static final int CODE_LENGTH = 6;
    private static final int EXPIRE_HOURS = 24;
    private static final long CLEANUP_INTERVAL_MILLIS = 3600_000L;
    private static final long CLEANUP_SHUTDOWN_WAIT_MILLIS = 5_000L;
    private static final AtomicBoolean CLEANUP_REGISTERED = new AtomicBoolean(false);
    private static final AtomicInteger CLEANUP_IN_FLIGHT = new AtomicInteger(0);
    private static final Object CLEANUP_MONITOR = new Object();
    private static volatile Long cleanupTimerId;
    private static volatile Vertx cleanupVertx;
    private final JDBCPool jdbcPool = JDBCPoolInit.instance().getPool();

    @Override
    public Future<String> submitMessage(String content, String host) {
        registerCleanup();
        Promise<String> promise = Promise.promise();
        String code = generateRandomCode();
        // 判断一下当前code是否存在消息
        LocalDateTime expireTime = LocalDateTime.now().plusHours(EXPIRE_HOURS);

        String sql = "INSERT INTO t_messages (code, content, expire_time, ip) VALUES (?, ?, ?, ?)";

        jdbcPool.preparedQuery(sql)
            .execute(Tuple.of(code, content,
                    java.sql.Timestamp.from(expireTime.atZone(ZoneId.systemDefault()).toInstant()),
                    host))
            .onSuccess(res -> {
                log.info("Message submitted with code: {}", code);
                promise.complete(code);
            })
            .onFailure(err -> {
                log.error("Failed to submit message", err);
                promise.fail(err);
            });

        return promise.future();
    }

    @Override
    public Future<JsonObject> retrieveMessage(String code) {
        registerCleanup();
        Promise<JsonObject> promise = Promise.promise();

        String sql = "SELECT content FROM t_messages WHERE code = ? AND expire_time > NOW()";

        jdbcPool.preparedQuery(sql)
            .execute(Tuple.of(code))
            .onSuccess(rows -> {
                if (rows.size() > 0) {
                    String content = rows.iterator().next().getString("content");
                    // 标记为已使用
                    markAsUsed(code);
                    promise.complete(JsonResult.data(content).toJsonObject());
                } else {
                    promise.fail("无效的提取码或消息已过期");
                }
            })
            .onFailure(err -> {
                log.error("Failed to retrieve message", err);
                promise.fail(err);
            });

        return promise.future();
    }

    private void markAsUsed(String code) {
        String sql = "UPDATE t_messages SET is_used = TRUE WHERE code = ?";
        jdbcPool.preparedQuery(sql).execute(Tuple.of(code));
    }

    private static void registerCleanup() {
        if (!CLEANUP_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        try {
            Vertx vertx = VertxHolder.getVertxInstance();
            cleanupVertx = vertx;
            cleanupTimerId = vertx.setPeriodic(CLEANUP_INTERVAL_MILLIS, CLEANUP_INTERVAL_MILLIS,
                    id -> cleanupExpiredMessages());
            cleanupExpiredMessages();
        } catch (Exception e) {
            cleanupTimerId = null;
            cleanupVertx = null;
            CLEANUP_REGISTERED.set(false);
            log.warn("注册消息清理任务失败", e);
        }
    }

    public static void cancelCleanup() {
        Long timerId = cleanupTimerId;
        Vertx vertx = cleanupVertx;
        cleanupTimerId = null;
        cleanupVertx = null;
        CLEANUP_REGISTERED.set(false);

        if (timerId == null || vertx == null) {
            waitForCleanupToFinish();
            return;
        }
        try {
            if (vertx.cancelTimer(timerId)) {
                log.info("消息定时清理任务已取消");
            }
        } catch (Exception e) {
            log.warn("取消消息定时清理任务失败", e);
        }
        waitForCleanupToFinish();
    }

    private static void cleanupExpiredMessages() {
        cleanupStarted();
        boolean asyncCleanupStarted = false;
        try {
            if (!CLEANUP_REGISTERED.get()) {
                return;
            }
            JDBCPoolInit poolInit = JDBCPoolInit.instance();
            if (poolInit == null || poolInit.getPool() == null) {
                log.debug("数据库连接池未就绪，跳过消息定时清理");
                return;
            }
            JDBCPool pool = poolInit.getPool();
            String sql = "DELETE FROM t_messages WHERE expire_time < NOW()";
            Future<io.vertx.sqlclient.RowSet<io.vertx.sqlclient.Row>> cleanupFuture = pool.query(sql).execute();
            asyncCleanupStarted = true;
            cleanupFuture.onSuccess(res -> {
                    if (res.rowCount() > 0) {
                        log.info("清理过期消息 {} 条", res.rowCount());
                    }
                })
                .onFailure(e -> log.warn("清理过期消息失败", e))
                .onComplete(ar -> cleanupFinished());
        } catch (Exception e) {
            log.warn("清理过期消息任务启动失败", e);
        } finally {
            if (!asyncCleanupStarted) {
                cleanupFinished();
            }
        }
    }

    private static void cleanupStarted() {
        CLEANUP_IN_FLIGHT.incrementAndGet();
    }

    private static void cleanupFinished() {
        if (CLEANUP_IN_FLIGHT.decrementAndGet() <= 0) {
            synchronized (CLEANUP_MONITOR) {
                CLEANUP_MONITOR.notifyAll();
            }
        }
    }

    private static void waitForCleanupToFinish() {
        long deadline = System.currentTimeMillis() + CLEANUP_SHUTDOWN_WAIT_MILLIS;
        synchronized (CLEANUP_MONITOR) {
            while (CLEANUP_IN_FLIGHT.get() > 0) {
                long waitMillis = deadline - System.currentTimeMillis();
                if (waitMillis <= 0) {
                    log.warn("等待消息定时清理结束超时，剩余任务数: {}", CLEANUP_IN_FLIGHT.get());
                    return;
                }
                try {
                    CLEANUP_MONITOR.wait(waitMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("等待消息定时清理结束被中断");
                    return;
                }
            }
        }
    }

    private String generateRandomCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
