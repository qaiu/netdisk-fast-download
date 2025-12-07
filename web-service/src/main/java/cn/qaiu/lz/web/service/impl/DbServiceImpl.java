package cn.qaiu.lz.web.service.impl;

import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.lz.common.model.UserInfo;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.lz.web.model.StatisticsInfo;
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

import java.util.ArrayList;
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
    @Override
    public Future<JsonObject> sayOk(String data) {
        log.info("say ok1 -> wait...");
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return Future.succeededFuture(JsonObject.mapFrom(JsonResult.data("Hi: " + data)));
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
        String sql = "SELECT * FROM playground_parser ORDER BY create_time DESC";

        client.query(sql).execute().onSuccess(rows -> {
            List<JsonObject> list = new ArrayList<>();
            for (Row row : rows) {
                JsonObject parser = new JsonObject();
                parser.put("id", row.getLong("id"));
                parser.put("name", row.getString("name"));
                parser.put("type", row.getString("type"));
                parser.put("displayName", row.getString("display_name"));
                parser.put("description", row.getString("description"));
                parser.put("author", row.getString("author"));
                parser.put("version", row.getString("version"));
                parser.put("matchPattern", row.getString("match_pattern"));
                parser.put("jsCode", row.getString("js_code"));
                parser.put("ip", row.getString("ip"));
                // 将LocalDateTime转换为字符串格式，避免序列化为数组
                var createTime = row.getLocalDateTime("create_time");
                if (createTime != null) {
                    parser.put("createTime", createTime.toString().replace("T", " "));
                }
                var updateTime = row.getLocalDateTime("update_time");
                if (updateTime != null) {
                    parser.put("updateTime", updateTime.toString().replace("T", " "));
                }
                parser.put("enabled", row.getBoolean("enabled"));
                list.add(parser);
            }
            promise.complete(JsonResult.data(list).toJsonObject());
        }).onFailure(e -> {
            log.error("getPlaygroundParserList failed", e);
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
            SET name = ?, display_name = ?, description = ?, author = ?, 
                version = ?, match_pattern = ?, js_code = ?, update_time = NOW(), enabled = ?
            WHERE id = ?
            """;

        client.preparedQuery(sql)
            .execute(Tuple.of(
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
    public Future<JsonObject> getPlaygroundParserById(Long id) {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<JsonObject> promise = Promise.promise();
        
        String sql = "SELECT * FROM playground_parser WHERE id = ?";

        client.preparedQuery(sql)
            .execute(Tuple.of(id))
            .onSuccess(rows -> {
                if (rows.size() > 0) {
                    Row row = rows.iterator().next();
                    JsonObject parser = new JsonObject();
                    parser.put("id", row.getLong("id"));
                    parser.put("name", row.getString("name"));
                    parser.put("type", row.getString("type"));
                    parser.put("displayName", row.getString("display_name"));
                    parser.put("description", row.getString("description"));
                    parser.put("author", row.getString("author"));
                    parser.put("version", row.getString("version"));
                    parser.put("matchPattern", row.getString("match_pattern"));
                    parser.put("jsCode", row.getString("js_code"));
                    parser.put("ip", row.getString("ip"));
                    // 将LocalDateTime转换为字符串格式，避免序列化为数组
                    var createTime = row.getLocalDateTime("create_time");
                    if (createTime != null) {
                        parser.put("createTime", createTime.toString().replace("T", " "));
                    }
                    var updateTime = row.getLocalDateTime("update_time");
                    if (updateTime != null) {
                        parser.put("updateTime", updateTime.toString().replace("T", " "));
                    }
                    parser.put("enabled", row.getBoolean("enabled"));
                    promise.complete(JsonResult.data(parser).toJsonObject());
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

    @Override
    public Future<JsonObject> saveTypeScriptCode(JsonObject tsCodeInfo) {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<JsonObject> promise = Promise.promise();
        
        String sql = """
            INSERT INTO playground_typescript_code 
            (parser_id, ts_code, es5_code, compile_errors, compiler_version, 
             compile_options, create_time, is_valid, ip)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), ?, ?)
            """;

        client.preparedQuery(sql)
            .execute(Tuple.of(
                tsCodeInfo.getLong("parserId"),
                tsCodeInfo.getString("tsCode"),
                tsCodeInfo.getString("es5Code"),
                tsCodeInfo.getString("compileErrors"),
                tsCodeInfo.getString("compilerVersion"),
                tsCodeInfo.getString("compileOptions"),
                tsCodeInfo.getBoolean("isValid", true),
                tsCodeInfo.getString("ip")
            ))
            .onSuccess(res -> {
                promise.complete(JsonResult.success("保存TypeScript代码成功").toJsonObject());
            })
            .onFailure(e -> {
                log.error("saveTypeScriptCode failed", e);
                promise.fail(e);
            });

        return promise.future();
    }

    @Override
    public Future<JsonObject> getTypeScriptCodeByParserId(Long parserId) {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<JsonObject> promise = Promise.promise();
        
        String sql = "SELECT * FROM playground_typescript_code WHERE parser_id = ? ORDER BY create_time DESC LIMIT 1";

        client.preparedQuery(sql)
            .execute(Tuple.of(parserId))
            .onSuccess(rows -> {
                if (rows.size() > 0) {
                    Row row = rows.iterator().next();
                    JsonObject tsCode = new JsonObject();
                    tsCode.put("id", row.getLong("id"));
                    tsCode.put("parserId", row.getLong("parser_id"));
                    tsCode.put("tsCode", row.getString("ts_code"));
                    tsCode.put("es5Code", row.getString("es5_code"));
                    tsCode.put("compileErrors", row.getString("compile_errors"));
                    tsCode.put("compilerVersion", row.getString("compiler_version"));
                    tsCode.put("compileOptions", row.getString("compile_options"));
                    var createTime = row.getLocalDateTime("create_time");
                    if (createTime != null) {
                        tsCode.put("createTime", createTime.toString().replace("T", " "));
                    }
                    var updateTime = row.getLocalDateTime("update_time");
                    if (updateTime != null) {
                        tsCode.put("updateTime", updateTime.toString().replace("T", " "));
                    }
                    tsCode.put("isValid", row.getBoolean("is_valid"));
                    tsCode.put("ip", row.getString("ip"));
                    promise.complete(JsonResult.data(tsCode).toJsonObject());
                } else {
                    promise.complete(JsonResult.data(null).toJsonObject());
                }
            })
            .onFailure(e -> {
                log.error("getTypeScriptCodeByParserId failed", e);
                promise.fail(e);
            });

        return promise.future();
    }

    @Override
    public Future<JsonObject> updateTypeScriptCode(Long parserId, JsonObject tsCodeInfo) {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<JsonObject> promise = Promise.promise();
        
        String sql = """
            UPDATE playground_typescript_code 
            SET ts_code = ?, es5_code = ?, compile_errors = ?, compiler_version = ?,
                compile_options = ?, update_time = NOW(), is_valid = ?
            WHERE parser_id = ?
            """;

        client.preparedQuery(sql)
            .execute(Tuple.of(
                tsCodeInfo.getString("tsCode"),
                tsCodeInfo.getString("es5Code"),
                tsCodeInfo.getString("compileErrors"),
                tsCodeInfo.getString("compilerVersion"),
                tsCodeInfo.getString("compileOptions"),
                tsCodeInfo.getBoolean("isValid", true),
                parserId
            ))
            .onSuccess(res -> {
                promise.complete(JsonResult.success("更新TypeScript代码成功").toJsonObject());
            })
            .onFailure(e -> {
                log.error("updateTypeScriptCode failed", e);
                promise.fail(e);
            });

        return promise.future();
    }
}
