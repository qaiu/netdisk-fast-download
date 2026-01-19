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
            // @type         example_js
            // @displayName  JS示例
            // @version      1.0.0
            // @author       System
            // @match        https?://httpbin\\.org/s/(?<KEY>\\w+)
            // ==/UserScript==
            
            /**
             * 解析单个文件下载链接
             * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息对象
             * @param {JsHttpClient} http - HTTP客户端实例
             * @param {JsLogger} logger - 日志记录器实例
             * @returns {string} 下载链接
             */
            function parse(shareLinkInfo, http, logger) {
                logger.info("===== JS示例解析器 =====");
                
                var shareUrl = shareLinkInfo.getShareUrl();
                var shareKey = shareLinkInfo.getShareKey();
                logger.info("分享链接: " + shareUrl);
                logger.info("分享Key: " + shareKey);
                
                // 使用内置HTTP客户端发送GET请求
                var response = http.get("https://httpbin.org/html");
                
                if (response.statusCode() === 200) {
                    var body = response.text();
                    logger.info("获取到HTML内容，长度: " + body.length);
                    
                    // 提取标题
                    var titleMatch = body.match(/<title>([^<]+)<\\/title>/i);
                    var title = titleMatch ? titleMatch[1] : "未知标题";
                    logger.info("页面标题: " + title);
                    
                    // 返回下载链接（示例：返回HTML页面URL）
                    return "https://httpbin.org/html";
                } else {
                    logger.error("请求失败，状态码: " + response.statusCode());
                    throw new Error("请求失败: " + response.statusCode());
                }
            }
            
            /**
             * 解析文件列表（可选）
             * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息对象
             * @param {JsHttpClient} http - HTTP客户端实例
             * @param {JsLogger} logger - 日志记录器实例
             * @returns {FileInfo[]} 文件信息列表
             */
            function parseFileList(shareLinkInfo, http, logger) {
                logger.info("===== 解析文件列表 =====");
                
                var response = http.get("https://httpbin.org/json");
                var data = response.json();
                
                // 返回文件列表
                return [{
                    fileName: "example.html",
                    fileId: "1",
                    fileType: "file",
                    size: 1024,
                    sizeStr: "1 KB",
                    parserUrl: "https://httpbin.org/html"
                }];
            }
            """;
        
        // Python 示例解析器代码
        String pyExampleCode = """
            # ==UserScript==
            # @name         示例Python解析器
            # @type         example_py
            # @displayName  Python示例
            # @description  演示如何编写Python解析器，使用requests库和正则表达式
            # @match        https?://httpbin\\.org/s/(?P<KEY>\\w+)
            # @author       System
            # @version      1.0.0
            # ==/UserScript==
            
            \"\"\"
            Python解析器示例 - 使用GraalPy运行
            
            可用模块：
            - requests: HTTP请求库 (已内置，支持 get/post/put/delete 等)
            - re: 正则表达式
            - json: JSON处理
            - base64: Base64编解码
            - hashlib: 哈希算法
            
            内置对象：
            - share_link_info: 分享链接信息
            - http: 底层HTTP客户端（PyHttpClient）
            - logger: 日志记录器（PyLogger）
            - crypto: 加密工具 (md5/sha1/sha256/aes/base64)
            \"\"\"
            
            import requests
            import re
            import json
            
            def parse(share_link_info, http, logger):
                \"\"\"
                解析单个文件下载链接
                
                Args:
                    share_link_info: 分享链接信息对象
                    http: HTTP客户端
                    logger: 日志记录器
                
                Returns:
                    str: 直链下载地址
                \"\"\"
                url = share_link_info.get_share_url()
                key = share_link_info.get_share_key()
                pwd = share_link_info.get_share_password()
                
                logger.info("===== Python示例解析器 =====")
                logger.info(f"分享链接: {url}")
                logger.info(f"分享Key: {key}")
                
                # 方式1：使用 requests 库发起请求（推荐）
                response = requests.get('https://httpbin.org/html', headers={
                    "Referer": url,
                    "User-Agent": "Mozilla/5.0"
                })
                
                if response.status_code != 200:
                    logger.error(f"请求失败: {response.status_code}")
                    raise Exception(f"请求失败: {response.status_code}")
                
                html = response.text
                logger.info(f"获取到HTML内容，长度: {len(html)}")
                
                # 示例：使用正则表达式提取标题
                match = re.search(r'<title>([^<]+)</title>', html, re.IGNORECASE)
                if match:
                    title = match.group(1)
                    logger.info(f"页面标题: {title}")
                
                # 方式2：使用内置HTTP客户端（适合简单场景）
                # json_response = http.get("https://httpbin.org/json")
                # data = json_response.json()
                # logger.info(f"JSON数据: {data.get('slideshow', {}).get('title', '未知')}")
                
                # 返回下载链接
                return "https://httpbin.org/html"
            
            def parse_file_list(share_link_info, http, logger):
                \"\"\"
                解析文件列表（可选）
            
                Args:
                    share_link_info: 分享链接信息对象
                    http: HTTP客户端
                    logger: 日志记录器
            
                Returns:
                    list: 文件信息列表
                \"\"\"
                dir_id = share_link_info.get_other_param("dirId") or "0"
                logger.info(f"解析文件列表，目录ID: {dir_id}")
                
                # 使用requests获取文件列表
                response = requests.get('https://httpbin.org/json')
                data = response.json()
                
                # 构建文件列表
                file_list = [
                    {
                        "fileName": "example.html",
                        "fileId": "1",
                        "fileType": "file",
                        "size": 2048,
                        "sizeStr": "2 KB",
                        "createTime": "2026-01-15 12:00:00",
                        "parserUrl": "https://httpbin.org/html"
                    },
                    {
                        "fileName": "subfolder",
                        "fileId": "2",
                        "fileType": "folder",
                        "size": 0,
                        "sizeStr": "-",
                        "parserUrl": ""
                    }
                ]
                
                logger.info(f"返回 {len(file_list)} 个文件/文件夹")
                return file_list
            """;
        
        // 先检查JS示例是否存在
        existsPlaygroundParserByType("example_js").compose(jsExists -> {
            if (jsExists) {
                log.info("JS示例解析器已存在，跳过初始化");
                return Future.succeededFuture();
            }
            // 插入JS示例解析器
            JsonObject jsParser = new JsonObject()
                .put("name", "示例JS解析器")
                .put("type", "example_js")
                .put("displayName", "JS示例")
                .put("description", "演示如何编写JavaScript解析器")
                .put("author", "System")
                .put("version", "1.0.0")
                .put("matchPattern", "https?://httpbin\\.org/s/(?<KEY>\\w+)")
                .put("jsCode", jsExampleCode)
                .put("language", "javascript")
                .put("ip", "127.0.0.1")
                .put("enabled", false);  // 默认禁用，避免干扰正常解析
            return savePlaygroundParser(jsParser);
        }).compose(v -> {
            // 检查Python示例是否存在
            return existsPlaygroundParserByType("example_py");
        }).compose(pyExists -> {
            if (pyExists) {
                log.info("Python示例解析器已存在，跳过初始化");
                return Future.succeededFuture();
            }
            // 插入Python示例解析器
            JsonObject pyParser = new JsonObject()
                .put("name", "示例Python解析器")
                .put("type", "example_py")
                .put("displayName", "Python示例")
                .put("description", "演示如何编写Python解析器")
                .put("author", "System")
                .put("version", "1.0.0")
                .put("matchPattern", "https?://httpbin\\.org/s/(?P<KEY>\\w+)")
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
