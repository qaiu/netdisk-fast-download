package cn.qaiu.lz.web.service.impl;

import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.lz.common.model.UserInfo;
import cn.qaiu.lz.web.model.StatisticsInfo;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.lz.web.util.CryptoUtil;
import cn.qaiu.vx.core.annotaions.Service;
import cn.qaiu.vx.core.model.JsonResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.templates.SqlTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

/**
 * lz-web
 * <br>Create date 2021/7/12 17:26
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Slf4j
@Service
public class DbServiceImpl implements DbService {
    private static final int DONATED_ACCOUNT_DISABLE_THRESHOLD = 3;
    private static final long FAILURE_TOKEN_TTL_MILLIS = 10 * 60 * 1000L;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DONATED_ACCOUNT_TOKEN_SIGN_KEY_CONFIG = "donatedAccountFailureTokenSignKey";
    private static final String DONATED_ACCOUNT_TOKEN_SIGN_KEY_FALLBACK = "nfd_donate_fail_token_sign_2026";
    @Override
    public Future<JsonObject> sayOk(String data) {
        log.info("say ok1 -> wait...");
        Promise<JsonObject> promise = Promise.promise();
        cn.qaiu.vx.core.util.VertxHolder.getVertxInstance().setTimer(4000, id -> {
            promise.complete(JsonObject.mapFrom(JsonResult.data("Hi: " + data)));
        });
        return promise.future();
    }

    @Override
    public Future<JsonObject> sayOk2(String data, UserInfo holder) {
//        val context = VertxHolder.getVertxInstance().getOrCreateContext();
//        log.info("say ok2 -> " + context.get("username"));
//        log.info("--> {}", holder.toString());
        return Future.succeededFuture(JsonObject.mapFrom(JsonResult.data("Hi: " + data)));
    }

    @Override
    public Future<StatisticsInfo> getStatisticsInfo() {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<StatisticsInfo> promise = Promise.promise();
        String sql = """
                select sum(api_parser_total) as parserTotal, sum(cache_hit_total) as cacheTotal,
                sum(api_parser_total) + sum(cache_hit_total) as total
                from api_statistics_info;
                """;

        SqlTemplate.forQuery(client, sql).mapTo(StatisticsInfo.class).execute(new HashMap<>()).onSuccess(row -> {
            StatisticsInfo info;
            if ((info = row.iterator().next()) != null) {
                promise.complete(info);
            } else {
                promise.fail("t_parser_log_info查询为空");
            }
        }).onFailure(e->{
            log.error("getStatisticsInfo: ", e);
            promise.fail(e);
        });
        return promise.future();
    }

    @Override
    public Future<JsonObject> getPlaygroundParserList() {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<JsonObject> promise = Promise.promise();
        String sql = """
                SELECT id, name, type, display_name, description, author, version,
                       match_pattern, ip, create_time, update_time, enabled
                FROM playground_parser
                ORDER BY create_time DESC
                LIMIT 100
                """;

        client.query(sql).execute().onSuccess(rows -> {
            List<JsonObject> list = new ArrayList<>();
            for (Row row : rows) {
                list.add(toPlaygroundParserJson(row, false));
            }
            promise.complete(JsonResult.data(list).toJsonObject());
        }).onFailure(e -> {
            log.error("getPlaygroundParserList failed", e);
            promise.fail(e);
        });

        return promise.future();
    }

    @Override
    public Future<JsonObject> getEnabledPlaygroundParsersForLoad() {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<JsonObject> promise = Promise.promise();
        String sql = """
                SELECT id, name, type, display_name, description, author, version,
                       match_pattern, js_code, ip, create_time, update_time, enabled
                FROM playground_parser
                WHERE enabled = TRUE
                ORDER BY update_time DESC, create_time DESC
                LIMIT 100
                """;

        client.query(sql).execute().onSuccess(rows -> {
            List<JsonObject> list = new ArrayList<>();
            for (Row row : rows) {
                list.add(toPlaygroundParserJson(row, true));
            }
            promise.complete(JsonResult.data(list).toJsonObject());
        }).onFailure(e -> {
            log.error("getEnabledPlaygroundParsersForLoad failed", e);
            promise.fail(e);
        });

        return promise.future();
    }

    @Override
    public Future<JsonObject> savePlaygroundParser(JsonObject parser) {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<JsonObject> promise = Promise.promise();
        
        String sql = """
            INSERT INTO playground_parser 
            (name, type, display_name, description, author, version, match_pattern, js_code, ip, create_time, enabled)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?)
            """;

        client.preparedQuery(sql)
            .execute(Tuple.of(
                parser.getString("name"),
                parser.getString("type"),
                parser.getString("displayName"),
                parser.getString("description"),
                parser.getString("author"),
                parser.getString("version"),
                parser.getString("matchPattern"),
                parser.getString("jsCode"),
                parser.getString("ip"),
                parser.getBoolean("enabled", true)
            ))
            .onSuccess(res -> {
                promise.complete(JsonResult.success("保存成功").toJsonObject());
            })
            .onFailure(e -> {
                log.error("savePlaygroundParser failed", e);
                promise.fail(e);
            });

        return promise.future();
    }

    @Override
    public Future<JsonObject> updatePlaygroundParser(Long id, JsonObject parser) {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<JsonObject> promise = Promise.promise();
        
        String sql = """
            UPDATE playground_parser 
            SET type = ?, name = ?, display_name = ?, description = ?, author = ?,
                version = ?, match_pattern = ?, js_code = ?, update_time = NOW(), enabled = ?
            WHERE id = ?
            """;

        client.preparedQuery(sql)
            .execute(Tuple.of(
                parser.getString("type"),
                parser.getString("name"),
                parser.getString("displayName"),
                parser.getString("description"),
                parser.getString("author"),
                parser.getString("version"),
                parser.getString("matchPattern"),
                parser.getString("jsCode"),
                parser.getBoolean("enabled", true),
                id
            ))
            .onSuccess(res -> {
                if (res.rowCount() == 0) {
                    promise.complete(JsonResult.error("解析器不存在").toJsonObject());
                    return;
                }
                promise.complete(JsonResult.success("更新成功").toJsonObject());
            })
            .onFailure(e -> {
                log.error("updatePlaygroundParser failed", e);
                promise.fail(e);
            });

        return promise.future();
    }

    @Override
    public Future<JsonObject> deletePlaygroundParser(Long id) {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<JsonObject> promise = Promise.promise();
        
        String sql = "DELETE FROM playground_parser WHERE id = ?";

        client.preparedQuery(sql)
            .execute(Tuple.of(id))
            .onSuccess(res -> {
                promise.complete(JsonResult.success("删除成功").toJsonObject());
            })
            .onFailure(e -> {
                log.error("deletePlaygroundParser failed", e);
                promise.fail(e);
            });

        return promise.future();
    }

    @Override
    public Future<Integer> getPlaygroundParserCount() {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<Integer> promise = Promise.promise();
        
        String sql = "SELECT COUNT(*) as count FROM playground_parser";

        client.query(sql).execute().onSuccess(rows -> {
            Integer count = rows.iterator().next().getInteger("count");
            promise.complete(count);
        }).onFailure(e -> {
            log.error("getPlaygroundParserCount failed", e);
            promise.fail(e);
        });

        return promise.future();
    }

    @Override
    public Future<Boolean> playgroundParserTypeExists(String type, Long excludeId) {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<Boolean> promise = Promise.promise();

        String sql = excludeId == null
                ? "SELECT COUNT(*) as count FROM playground_parser WHERE type = ?"
                : "SELECT COUNT(*) as count FROM playground_parser WHERE type = ? AND id <> ?";
        Tuple params = excludeId == null ? Tuple.of(type) : Tuple.of(type, excludeId);

        client.preparedQuery(sql).execute(params).onSuccess(rows -> {
            Integer count = rows.iterator().next().getInteger("count");
            promise.complete(count != null && count > 0);
        }).onFailure(e -> {
            log.error("playgroundParserTypeExists failed", e);
            promise.fail(e);
        });

        return promise.future();
    }

    @Override
    public Future<JsonObject> getPlaygroundParserById(Long id) {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<JsonObject> promise = Promise.promise();
        
        String sql = "SELECT * FROM playground_parser WHERE id = ?";

        client.preparedQuery(sql)
            .execute(Tuple.of(id))
            .onSuccess(rows -> {
                if (rows.size() > 0) {
                    Row row = rows.iterator().next();
                    promise.complete(JsonResult.data(toPlaygroundParserJson(row, true)).toJsonObject());
                } else {
                    promise.fail("解析器不存在");
                }
            })
            .onFailure(e -> {
                log.error("getPlaygroundParserById failed", e);
                promise.fail(e);
            });

        return promise.future();
    }

    private JsonObject toPlaygroundParserJson(Row row, boolean includeJsCode) {
        JsonObject parser = new JsonObject();
        parser.put("id", row.getLong("id"));
        parser.put("name", row.getString("name"));
        parser.put("type", row.getString("type"));
        parser.put("displayName", row.getString("display_name"));
        parser.put("description", row.getString("description"));
        parser.put("author", row.getString("author"));
        parser.put("version", row.getString("version"));
        parser.put("matchPattern", row.getString("match_pattern"));
        if (includeJsCode) {
            parser.put("jsCode", row.getString("js_code"));
        }
        parser.put("ip", row.getString("ip"));
        var createTime = row.getLocalDateTime("create_time");
        if (createTime != null) {
            parser.put("createTime", createTime.toString().replace("T", " "));
        }
        var updateTime = row.getLocalDateTime("update_time");
        if (updateTime != null) {
            parser.put("updateTime", updateTime.toString().replace("T", " "));
        }
        parser.put("enabled", row.getBoolean("enabled"));
        return parser;
    }

    // ========== 捐赠账号相关 ==========

    @Override
    public Future<JsonObject> saveDonatedAccount(JsonObject account) {
        JDBCPool client = JDBCPoolInit.instance().getPool();

        // 只保留当前认证方式实际用到的字段，避免调用方切换认证类型后遗留的用户名/密码脏数据
        // 被一并存入库中，导致后续解析时被误当作真实凭证使用（例如把废弃的用户名当手机号登录）。
        boolean isPasswordAuth = "password".equalsIgnoreCase(account.getString("authType"));
        String usernameToStore = isPasswordAuth ? account.getString("username") : null;
        String passwordToStore = isPasswordAuth ? account.getString("password") : null;
        String tokenToStore = isPasswordAuth ? null : account.getString("token");

        Future<String> encryptedUsername = CryptoUtil.encrypt(usernameToStore);
        Future<String> encryptedPassword = CryptoUtil.encrypt(passwordToStore);
        Future<String> encryptedToken = CryptoUtil.encrypt(tokenToStore);

        return ensureFailCountColumn(client).compose(v ->
                Future.all(encryptedUsername, encryptedPassword, encryptedToken).compose(compositeFuture -> {
                    String sql = """
                        INSERT INTO donated_account
                        (pan_type, auth_type, username, password, token, remark, ip, enabled, fail_count, create_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, true, 0, NOW())
                        """;

                    return client.preparedQuery(sql)
                            .execute(Tuple.of(
                                    account.getString("panType"),
                                    account.getString("authType"),
                                    encryptedUsername.result(),
                                    encryptedPassword.result(),
                                    encryptedToken.result(),
                                    account.getString("remark"),
                                    account.getString("ip")
                            ))
                            .map(res -> JsonResult.success("捐赠成功").toJsonObject())
                            .onFailure(e -> log.error("saveDonatedAccount failed", e));
                }));
    }

    @Override
    public Future<JsonObject> getDonatedAccountCounts() {
        JDBCPool client = JDBCPoolInit.instance().getPool();

        String sql = "SELECT pan_type, enabled, COUNT(*) as count FROM donated_account GROUP BY pan_type, enabled";

        return client.query(sql).execute().map(rows -> {
            JsonObject result = new JsonObject();
            JsonObject activeCounts = new JsonObject();
            JsonObject inactiveCounts = new JsonObject();
            int totalActive = 0;
            int totalInactive = 0;

            for (Row row : rows) {
                String panType = row.getString("pan_type");
                boolean enabled = row.getBoolean("enabled");
                int count = row.getInteger("count");

                if (enabled) {
                    activeCounts.put(panType, count);
                    totalActive += count;
                } else {
                    inactiveCounts.put(panType, count);
                    totalInactive += count;
                }
            }

            activeCounts.put("total", totalActive);
            inactiveCounts.put("total", totalInactive);

            result.put("active", activeCounts);
            result.put("inactive", inactiveCounts);

            return JsonResult.data(result).toJsonObject();
        }).onFailure(e -> log.error("getDonatedAccountCounts failed", e));
    }

    @Override
    public Future<JsonObject> getRandomDonatedAccount(String panType) {
        JDBCPool client = JDBCPoolInit.instance().getPool();

        String sql = "SELECT * FROM donated_account WHERE pan_type = ? AND enabled = true ORDER BY RAND() LIMIT 1";

        return client.preparedQuery(sql)
                .execute(Tuple.of(panType))
                .compose(rows -> {
                    if (rows.size() > 0) {
                        Row row = rows.iterator().next();

                        Future<String> usernameFuture = decryptOrPlain(row.getString("username"));
                        Future<String> passwordFuture = decryptOrPlain(row.getString("password"));
                        Future<String> tokenFuture = decryptOrPlain(row.getString("token"));
                        Future<String> failureTokenFuture = issueDonatedAccountFailureToken(row.getLong("id"));

                        return Future.all(usernameFuture, passwordFuture, tokenFuture, failureTokenFuture)
                                .map(compositeFuture -> {
                                    String username = usernameFuture.result();
                                    String password = passwordFuture.result();
                                    String token = tokenFuture.result();

                                    // 历史脏数据兜底：非 password 认证类型的账号不应该带用户名/密码
                                    // （例如切换认证类型前遗留的表单数据），否则会被解析器误当作真实账号密码去登录。
                                    boolean isPasswordAuth = "password".equalsIgnoreCase(row.getString("auth_type"));
                                    if (!isPasswordAuth) {
                                        username = null;
                                        password = null;
                                    }

                                    // 如果解密后没有任何可用凭证，返回空对象，避免把密文当作明文认证参数下发给前端
                                    if (StringUtils.isBlank(username) && StringUtils.isBlank(password) && StringUtils.isBlank(token)) {
                                        log.warn("random donated account has no usable credential after decrypt, accountId={}", row.getLong("id"));
                                        return JsonResult.data(new JsonObject()).toJsonObject();
                                    }

                                    JsonObject account = new JsonObject();
                                    account.put("authType", row.getString("auth_type"));
                                    account.put("username", username);
                                    account.put("password", password);
                                    account.put("token", token);
                                    account.put("donatedAccountToken", failureTokenFuture.result());
                                    return JsonResult.data(account).toJsonObject();
                                });
                    } else {
                        return Future.succeededFuture(JsonResult.data(new JsonObject()).toJsonObject());
                    }
                })
                .onFailure(e -> log.error("getRandomDonatedAccount failed", e));
    }

    @Override
    public Future<String> issueDonatedAccountFailureToken(Long accountId) {
        if (accountId == null) {
            return Future.failedFuture("accountId is null");
        }
        try {
            long issuedAt = System.currentTimeMillis();
            String payload = accountId + ":" + issuedAt;
            String signature = hmacSha256(payload);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                    + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.getBytes(StandardCharsets.UTF_8));
            return Future.succeededFuture(token);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<Void> recordDonatedAccountFailureByToken(String failureToken) {
        JDBCPool client = JDBCPoolInit.instance().getPool();

        Long accountId;
        try {
            accountId = parseAndVerifyFailureToken(failureToken);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }

        String updateSql = """
                UPDATE donated_account
                SET fail_count = fail_count + 1,
                    enabled = CASE
                        WHEN fail_count + 1 >= ? THEN false
                        ELSE enabled
                    END
                WHERE id = ?
                """;

        return ensureFailCountColumn(client)
                .compose(v -> client.preparedQuery(updateSql)
                        .execute(Tuple.of(DONATED_ACCOUNT_DISABLE_THRESHOLD, accountId)))
                .map(rows -> (Void) null)
                .onFailure(e -> log.error("recordDonatedAccountFailureByToken failed", e));
    }

    private Future<Void> ensureFailCountColumn(JDBCPool client) {
        Promise<Void> promise = Promise.promise();
        String sql = "ALTER TABLE donated_account ADD COLUMN IF NOT EXISTS fail_count INT DEFAULT 0 NOT NULL";
        client.query(sql).execute()
                .onSuccess(res -> promise.complete())
                .onFailure(e -> {
                    String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                    if (!(msg.contains("duplicate") || msg.contains("exists") || msg.contains("already"))) {
                        log.warn("ensure fail_count column failed, continue without schema migration", e);
                    }
                    promise.complete();
                });
        return promise.future();
    }

    private Future<String> decryptOrPlain(String value) {
        if (value == null) {
            return Future.succeededFuture(null);
        }
        if (!isLikelyEncrypted(value)) {
            return Future.succeededFuture(value);
        }
        return CryptoUtil.decrypt(value).recover(e -> {
            // value 看起来像密文但无法解密，通常是密钥轮换/不一致导致；
            // 不应回退为明文，否则会把密文误当 token/cookie 返回给调用方
            log.warn("decrypt donated account field failed, fallback to null to avoid ciphertext leakage", e);
            return Future.succeededFuture((String) null);
        });
    }

    private boolean isLikelyEncrypted(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return decoded.length > 16;
        } catch (Exception e) {
            return false;
        }
    }

    private Long parseAndVerifyFailureToken(String token) throws Exception {
        if (token == null || token.isBlank() || !token.contains(".")) {
            throw new IllegalArgumentException("invalid donated account token");
        }
        String[] parts = token.split("\\.", 2);
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String signature = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String expected = hmacSha256(payload);
        if (!expected.equals(signature)) {
            throw new IllegalArgumentException("donated account token signature invalid");
        }

        String[] payloadParts = payload.split(":", 2);
        if (payloadParts.length != 2) {
            throw new IllegalArgumentException("invalid donated account token payload");
        }
        Long accountId = Long.parseLong(payloadParts[0]);
        long issuedAt = Long.parseLong(payloadParts[1]);
        if (System.currentTimeMillis() - issuedAt > FAILURE_TOKEN_TTL_MILLIS) {
            throw new IllegalArgumentException("donated account token expired");
        }
        return accountId;
    }

    private String hmacSha256(String payload) throws Exception {
        String secret = getDonatedAccountFailureTokenSignKey();
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private String getDonatedAccountFailureTokenSignKey() {
        try {
            String configKey = cn.qaiu.vx.core.util.SharedDataUtil
                    .getJsonStringForServerConfig(DONATED_ACCOUNT_TOKEN_SIGN_KEY_CONFIG);
            if (StringUtils.isNotBlank(configKey)) {
                return configKey;
            }
        } catch (Exception e) {
            log.debug("读取捐赠账号失败计数签名密钥失败，使用默认值: {}", e.getMessage());
        }
        return DONATED_ACCOUNT_TOKEN_SIGN_KEY_FALLBACK;
    }
}

