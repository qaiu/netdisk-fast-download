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
                parser.put("language", row.getString("language") != null ? row.getString("language") : "javascript");
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
            (name, type, display_name, description, author, version, match_pattern, js_code, language, ip, create_time, enabled)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?)
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
                parser.getString("language", "javascript"),
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
                    parser.put("language", row.getString("language") != null ? row.getString("language") : "javascript");
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
    public Future<Boolean> existsPlaygroundParserByType(String type) {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<Boolean> promise = Promise.promise();
        
        String sql = "SELECT COUNT(*) as count FROM playground_parser WHERE type = ?";
        
        client.preparedQuery(sql)
            .execute(Tuple.of(type))
            .onSuccess(rows -> {
                Integer count = rows.iterator().next().getInteger("count");
                promise.complete(count > 0);
            })
            .onFailure(e -> {
                log.error("existsPlaygroundParserByType failed", e);
                promise.fail(e);
            });
        
        return promise.future();
    }

    @Override
    public Future<Void> initExampleParsers() {
        Promise<Void> promise = Promise.promise();
        
        // JS 示例解析器代码
        String jsExampleCode = """
            // ==UserScript==
            // @name         示例JS解析器
            // @description  演示如何编写JavaScript解析器，访问 https://httpbin.org/html 获取HTML内容
            // @type         example-js
            // @displayName  JS示例
            // @version      1.0.0
            // @author       System
            // @matchPattern ^https?://httpbin\\.org/.*$
            // ==/UserScript==
            
            /**
             * 解析入口函数
             * @param {string} url 分享链接URL
             * @param {string} pwd 提取码（可选）
             * @returns {object} 包含下载链接的结果对象
             */
            function parse(url, pwd) {
                log.info("开始解析: " + url);
                
                // 使用内置HTTP客户端发送GET请求
                var response = http.get("https://httpbin.org/html");
                
                if (response.statusCode === 200) {
                    var body = response.body;
                    log.info("获取到HTML内容，长度: " + body.length);
                    
                    // 提取标题
                    var titleMatch = body.match(/<title>([^<]+)<\\/title>/i);
                    var title = titleMatch ? titleMatch[1] : "未知标题";
                    
                    // 返回结果
                    return {
                        downloadUrl: "https://httpbin.org/html",
                        fileName: title + ".html",
                        fileSize: body.length,
                        extra: {
                            title: title,
                            contentType: "text/html"
                        }
                    };
                } else {
                    log.error("请求失败，状态码: " + response.statusCode);
                    throw new Error("请求失败: " + response.statusCode);
                }
            }
            """;
        
        // Python 示例解析器代码
        String pyExampleCode = """
            # ==UserScript==
            # @name         示例Python解析器
            # @description  演示如何编写Python解析器，访问 https://httpbin.org/json 获取JSON数据
            # @type         example-py
            # @displayName  Python示例
            # @version      1.0.0
            # @author       System
            # @matchPattern ^https?://httpbin\\.org/.*$
            # ==/UserScript==
            
            def parse(url: str, pwd: str = None) -> dict:
                \"\"\"
                解析入口函数
                
                Args:
                    url: 分享链接URL
                    pwd: 提取码（可选）
                    
                Returns:
                    包含下载链接的结果字典
                \"\"\"
                log.info(f"开始解析: {url}")
                
                # 使用内置HTTP客户端发送GET请求
                response = http.get("https://httpbin.org/json")
                
                if response['statusCode'] == 200:
                    body = response['body']
                    log.info(f"获取到JSON内容，长度: {len(body)}")
                    
                    # 解析JSON
                    import json
                    data = json.loads(body)
                    
                    # 返回结果
                    return {
                        "downloadUrl": "https://httpbin.org/json",
                        "fileName": "data.json",
                        "fileSize": len(body),
                        "extra": {
                            "title": data.get("slideshow", {}).get("title", "未知"),
                            "contentType": "application/json"
                        }
                    }
                else:
                    log.error(f"请求失败，状态码: {response['statusCode']}")
                    raise Exception(f"请求失败: {response['statusCode']}")
            """;
        
        // 先检查JS示例是否存在
        existsPlaygroundParserByType("example-js").compose(jsExists -> {
            if (jsExists) {
                log.info("JS示例解析器已存在，跳过初始化");
                return Future.succeededFuture();
            }
            // 插入JS示例解析器
            JsonObject jsParser = new JsonObject()
                .put("name", "示例JS解析器")
                .put("type", "example-js")
                .put("displayName", "JS示例")
                .put("description", "演示如何编写JavaScript解析器")
                .put("author", "System")
                .put("version", "1.0.0")
                .put("matchPattern", "^https?://httpbin\\.org/.*$")
                .put("jsCode", jsExampleCode)
                .put("language", "javascript")
                .put("ip", "127.0.0.1")
                .put("enabled", false);  // 默认禁用，避免干扰正常解析
            return savePlaygroundParser(jsParser);
        }).compose(v -> {
            // 检查Python示例是否存在
            return existsPlaygroundParserByType("example-py");
        }).compose(pyExists -> {
            if (pyExists) {
                log.info("Python示例解析器已存在，跳过初始化");
                return Future.succeededFuture();
            }
            // 插入Python示例解析器
            JsonObject pyParser = new JsonObject()
                .put("name", "示例Python解析器")
                .put("type", "example-py")
                .put("displayName", "Python示例")
                .put("description", "演示如何编写Python解析器")
                .put("author", "System")
                .put("version", "1.0.0")
                .put("matchPattern", "^https?://httpbin\\.org/.*$")
                .put("jsCode", pyExampleCode)
                .put("language", "python")
                .put("ip", "127.0.0.1")
                .put("enabled", false);  // 默认禁用，避免干扰正常解析
            return savePlaygroundParser(pyParser);
        }).onSuccess(v -> {
            log.info("示例解析器初始化完成");
            promise.complete();
        }).onFailure(e -> {
            log.error("初始化示例解析器失败", e);
            promise.fail(e);
        });
        
        return promise.future();
    }
}
