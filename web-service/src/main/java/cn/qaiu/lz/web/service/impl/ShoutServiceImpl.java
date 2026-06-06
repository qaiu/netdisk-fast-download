package cn.qaiu.lz.web.service.impl;

import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.lz.web.service.ShoutService;
import cn.qaiu.vx.core.annotaions.Service;
import cn.qaiu.vx.core.model.JsonResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Random;

@Slf4j
@Service
@SuppressWarnings("SqlResolve") // 这里是为了避免检查SQL语句的警告
public class ShoutServiceImpl implements ShoutService {
    private static final int CODE_LENGTH = 6;
    private static final int EXPIRE_HOURS = 24;
    private static volatile boolean cleanupRegistered = false;
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

    private void registerCleanup() {
        if (cleanupRegistered) {
            return;
        }
        synchronized (ShoutServiceImpl.class) {
            if (cleanupRegistered) {
                return;
            }
            cleanupRegistered = true;
            try {
                cn.qaiu.vx.core.util.VertxHolder.getVertxInstance()
                        .setPeriodic(3600_000, 3600_000, id -> cleanupExpiredMessages());
                cleanupExpiredMessages();
            } catch (Exception e) {
                cleanupRegistered = false;
                log.warn("注册消息清理任务失败", e);
            }
        }
    }

    private void cleanupExpiredMessages() {
        String sql = "DELETE FROM t_messages WHERE expire_time < NOW()";
        jdbcPool.query(sql).execute()
                .onSuccess(res -> {
                    if (res.rowCount() > 0) {
                        log.info("清理过期消息 {} 条", res.rowCount());
                    }
                })
                .onFailure(e -> log.warn("清理过期消息失败", e));
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
