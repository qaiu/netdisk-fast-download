package cn.qaiu.lz.web.controller;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.lz.web.model.PlaygroundTestResp;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.parser.customjs.JsPlaygroundExecutor;
import cn.qaiu.parser.customjs.JsPlaygroundLogger;
import cn.qaiu.parser.customjs.JsScriptMetadataParser;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.RouteMethod;
import cn.qaiu.vx.core.model.JsonResult;
import cn.qaiu.vx.core.util.AsyncServiceUtil;
import cn.qaiu.vx.core.util.ResponseUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 演练场API控制器
 * 提供JavaScript解析脚本的测试接口
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@RouteHandler(value = "/v2/playground", order = 10)
@Slf4j
public class PlaygroundApi {

    private static final int MAX_PARSER_COUNT = 100;
    private static final int MAX_CODE_LENGTH = 128 * 1024; // 128KB 代码长度限制
    private final DbService dbService = AsyncServiceUtil.getAsyncServiceInstance(DbService.class);

    /**
     * 测试执行JavaScript代码
     *
     * @param ctx 路由上下文
     * @return 测试结果
     */
    @RouteMapping(value = "/test", method = RouteMethod.POST)
    public Future<JsonObject> test(RoutingContext ctx) {
        Promise<JsonObject> promise = Promise.promise();

        try {
            JsonObject body = ctx.body().asJsonObject();
            String jsCode = body.getString("jsCode");
            String shareUrl = body.getString("shareUrl");
            String pwd = body.getString("pwd");
            String method = body.getString("method", "parse");

            // 参数验证
            if (StringUtils.isBlank(jsCode)) {
                promise.complete(JsonObject.mapFrom(PlaygroundTestResp.builder()
                        .success(false)
                        .error("JavaScript代码不能为空")
                        .build()));
                return promise.future();
            }
            
            // 代码长度验证
            if (jsCode.length() > MAX_CODE_LENGTH) {
                promise.complete(JsonObject.mapFrom(PlaygroundTestResp.builder()
                        .success(false)
                        .error("代码长度超过限制（最大128KB），当前长度: " + jsCode.length() + " 字节")
                        .build()));
                return promise.future();
            }

            if (StringUtils.isBlank(shareUrl)) {
                promise.complete(JsonObject.mapFrom(PlaygroundTestResp.builder()
                        .success(false)
                        .error("分享链接不能为空")
                        .build()));
                return promise.future();
            }

            // 验证方法类型
            if (!"parse".equals(method) && !"parseFileList".equals(method) && !"parseById".equals(method)) {
                promise.complete(JsonObject.mapFrom(PlaygroundTestResp.builder()
                        .success(false)
                        .error("方法类型无效，必须是 parse、parseFileList 或 parseById")
                        .build()));
                return promise.future();
            }

            long startTime = System.currentTimeMillis();

            try {
                // 创建ShareLinkInfo
                ParserCreate parserCreate = ParserCreate.fromShareUrl(shareUrl);
                if (StringUtils.isNotBlank(pwd)) {
                    parserCreate.setShareLinkInfoPwd(pwd);
                }
                ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();

                // 创建演练场执行器
                JsPlaygroundExecutor executor = new JsPlaygroundExecutor(shareLinkInfo, jsCode);

                // 根据方法类型选择执行，并异步处理结果
                Future<Object> executionFuture;
                switch (method) {
                    case "parse":
                        executionFuture = executor.executeParseAsync().map(r -> (Object) r);
                        break;
                    case "parseFileList":
                        executionFuture = executor.executeParseFileListAsync().map(r -> (Object) r);
                        break;
                    case "parseById":
                        executionFuture = executor.executeParseByIdAsync().map(r -> (Object) r);
                        break;
                    default:
                        promise.fail(new IllegalArgumentException("未知的方法类型: " + method));
                        return promise.future();
                }

                // 异步处理执行结果
                executionFuture.onSuccess(result -> {
                    log.debug("执行成功，结果类型: {}, 结果值: {}", 
                            result != null ? result.getClass().getSimpleName() : "null", 
                            result);
                    
                    // 获取日志
                    List<JsPlaygroundLogger.LogEntry> logEntries = executor.getLogs();
                    log.debug("获取到 {} 条日志记录", logEntries.size());
                    
                    List<PlaygroundTestResp.LogEntry> respLogs = logEntries.stream()
                            .map(entry -> PlaygroundTestResp.LogEntry.builder()
                                    .level(entry.getLevel())
                                    .message(entry.getMessage())
                                    .timestamp(entry.getTimestamp())
                                    .source(entry.getSource())  // 使用日志条目的来源标识
                                    .build())
                            .collect(Collectors.toList());

                    long executionTime = System.currentTimeMillis() - startTime;

                    // 构建响应
                    PlaygroundTestResp response = PlaygroundTestResp.builder()
                            .success(true)
                            .result(result)
                            .logs(respLogs)
                            .executionTime(executionTime)
                            .build();

                    JsonObject jsonResponse = JsonObject.mapFrom(response);
                    log.debug("测试成功响应: {}", jsonResponse.encodePrettily());
                    promise.complete(jsonResponse);
                }).onFailure(e -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    String errorMessage = e.getMessage();
                    String stackTrace = getStackTrace(e);

                    log.error("演练场执行失败", e);

                    // 尝试获取已有的日志
                    List<JsPlaygroundLogger.LogEntry> logEntries = executor.getLogs();
                    List<PlaygroundTestResp.LogEntry> respLogs = logEntries.stream()
                            .map(entry -> PlaygroundTestResp.LogEntry.builder()
                                    .level(entry.getLevel())
                                    .message(entry.getMessage())
                                    .timestamp(entry.getTimestamp())
                                    .source(entry.getSource())  // 使用日志条目的来源标识
                                    .build())
                            .collect(Collectors.toList());

                    PlaygroundTestResp response = PlaygroundTestResp.builder()
                            .success(false)
                            .error(errorMessage)
                            .stackTrace(stackTrace)
                            .executionTime(executionTime)
                            .logs(respLogs)
                            .build();

                    promise.complete(JsonObject.mapFrom(response));
                });

            } catch (Exception e) {
                long executionTime = System.currentTimeMillis() - startTime;
                String errorMessage = e.getMessage();
                String stackTrace = getStackTrace(e);

                log.error("演练场初始化失败", e);

                PlaygroundTestResp response = PlaygroundTestResp.builder()
                        .success(false)
                        .error(errorMessage)
                        .stackTrace(stackTrace)
                        .executionTime(executionTime)
                        .logs(new ArrayList<>())
                        .build();

                promise.complete(JsonObject.mapFrom(response));
            }
        } catch (Exception e) {
            log.error("解析请求参数失败", e);
            promise.complete(JsonObject.mapFrom(PlaygroundTestResp.builder()
                    .success(false)
                    .error("解析请求参数失败: " + e.getMessage())
                    .stackTrace(getStackTrace(e))
                    .build()));
        }

        return promise.future();
    }

    /**
     * 获取types.js文件内容
     *
     * @param response HTTP响应
     */
    @RouteMapping(value = "/types.js", method = RouteMethod.GET)
    public void getTypesJs(HttpServerResponse response) {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("custom-parsers/types.js")) {

            if (inputStream == null) {
                ResponseUtil.fireJsonResultResponse(response, JsonResult.error("types.js文件不存在"));
                return;
            }

            String content = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            response.putHeader("Content-Type", "text/javascript; charset=utf-8")
                    .end(content);

        } catch (Exception e) {
            log.error("读取types.js失败", e);
            ResponseUtil.fireJsonResultResponse(response, JsonResult.error("读取types.js失败: " + e.getMessage()));
        }
    }

    /**
     * 获取解析器列表
     */
    @RouteMapping(value = "/parsers", method = RouteMethod.GET)
    public Future<JsonObject> getParserList() {
        return dbService.getPlaygroundParserList();
    }

    /**
     * 保存解析器
     */
    @RouteMapping(value = "/parsers", method = RouteMethod.POST)
    public Future<JsonObject> saveParser(RoutingContext ctx) {
        Promise<JsonObject> promise = Promise.promise();

        try {
            JsonObject body = ctx.body().asJsonObject();
            String jsCode = body.getString("jsCode");

            if (StringUtils.isBlank(jsCode)) {
                promise.complete(JsonResult.error("JavaScript代码不能为空").toJsonObject());
                return promise.future();
            }
            
            // 代码长度验证
            if (jsCode.length() > MAX_CODE_LENGTH) {
                promise.complete(JsonResult.error("代码长度超过限制（最大128KB），当前长度: " + jsCode.length() + " 字节").toJsonObject());
                return promise.future();
            }

            // 解析元数据
            try {
                var config = JsScriptMetadataParser.parseScript(jsCode);
                String type = config.getType();
                String displayName = config.getDisplayName();
                String name = config.getMetadata().get("name");
                String description = config.getMetadata().get("description");
                String author = config.getMetadata().get("author");
                String version = config.getMetadata().get("version");
                String matchPattern = config.getMatchPattern() != null ? config.getMatchPattern().pattern() : null;

                // 检查数量限制
                dbService.getPlaygroundParserCount().onSuccess(count -> {
                    if (count >= MAX_PARSER_COUNT) {
                        promise.complete(JsonResult.error("解析器数量已达到上限（" + MAX_PARSER_COUNT + "个），请先删除不需要的解析器").toJsonObject());
                        return;
                    }

                    // 检查type是否已存在
                    dbService.getPlaygroundParserList().onSuccess(listResult -> {
                        var list = listResult.getJsonArray("data");
                        boolean exists = false;
                        if (list != null) {
                            for (int i = 0; i < list.size(); i++) {
                                var item = list.getJsonObject(i);
                                if (type.equals(item.getString("type"))) {
                                    exists = true;
                                    break;
                                }
                            }
                        }

                        if (exists) {
                            promise.complete(JsonResult.error("解析器类型 " + type + " 已存在，请使用其他类型标识").toJsonObject());
                            return;
                        }

                        // 保存到数据库
                        JsonObject parser = new JsonObject();
                        parser.put("name", name);
                        parser.put("type", type);
                        parser.put("displayName", displayName);
                        parser.put("description", description);
                        parser.put("author", author);
                        parser.put("version", version);
                        parser.put("matchPattern", matchPattern);
                        parser.put("jsCode", jsCode);
                        parser.put("ip", getClientIp(ctx.request()));
                        parser.put("enabled", true);

                        dbService.savePlaygroundParser(parser).onSuccess(result -> {
                            promise.complete(result);
                        }).onFailure(e -> {
                            log.error("保存解析器失败", e);
                            promise.complete(JsonResult.error("保存失败: " + e.getMessage()).toJsonObject());
                        });
                    }).onFailure(e -> {
                        log.error("获取解析器列表失败", e);
                        promise.complete(JsonResult.error("检查解析器失败: " + e.getMessage()).toJsonObject());
                    });
                }).onFailure(e -> {
                    log.error("获取解析器数量失败", e);
                    promise.complete(JsonResult.error("检查解析器数量失败: " + e.getMessage()).toJsonObject());
                });

            } catch (Exception e) {
                log.error("解析脚本元数据失败", e);
                promise.complete(JsonResult.error("解析脚本元数据失败: " + e.getMessage()).toJsonObject());
            }
        } catch (Exception e) {
            log.error("解析请求参数失败", e);
            promise.complete(JsonResult.error("解析请求参数失败: " + e.getMessage()).toJsonObject());
        }

        return promise.future();
    }

    /**
     * 更新解析器
     */
    @RouteMapping(value = "/parsers/:id", method = RouteMethod.PUT)
    public Future<JsonObject> updateParser(RoutingContext ctx, Long id) {
        Promise<JsonObject> promise = Promise.promise();

        try {
            JsonObject body = ctx.body().asJsonObject();
            String jsCode = body.getString("jsCode");

            if (StringUtils.isBlank(jsCode)) {
                promise.complete(JsonResult.error("JavaScript代码不能为空").toJsonObject());
                return promise.future();
            }

            // 解析元数据
            try {
                var config = JsScriptMetadataParser.parseScript(jsCode);
                String displayName = config.getDisplayName();
                String name = config.getMetadata().get("name");
                String description = config.getMetadata().get("description");
                String author = config.getMetadata().get("author");
                String version = config.getMetadata().get("version");
                String matchPattern = config.getMatchPattern() != null ? config.getMatchPattern().pattern() : null;

                JsonObject parser = new JsonObject();
                parser.put("name", name);
                parser.put("displayName", displayName);
                parser.put("description", description);
                parser.put("author", author);
                parser.put("version", version);
                parser.put("matchPattern", matchPattern);
                parser.put("jsCode", jsCode);
                parser.put("enabled", body.getBoolean("enabled", true));

                dbService.updatePlaygroundParser(id, parser).onSuccess(result -> {
                    promise.complete(result);
                }).onFailure(e -> {
                    log.error("更新解析器失败", e);
                    promise.complete(JsonResult.error("更新失败: " + e.getMessage()).toJsonObject());
                });

            } catch (Exception e) {
                log.error("解析脚本元数据失败", e);
                promise.complete(JsonResult.error("解析脚本元数据失败: " + e.getMessage()).toJsonObject());
            }
        } catch (Exception e) {
            log.error("解析请求参数失败", e);
            promise.complete(JsonResult.error("解析请求参数失败: " + e.getMessage()).toJsonObject());
        }

        return promise.future();
    }

    /**
     * 删除解析器
     */
    @RouteMapping(value = "/parsers/:id", method = RouteMethod.DELETE)
    public Future<JsonObject> deleteParser(Long id) {
        return dbService.deletePlaygroundParser(id);
    }

    /**
     * 根据ID获取解析器
     */
    @RouteMapping(value = "/parsers/:id", method = RouteMethod.GET)
    public Future<JsonObject> getParserById(Long id) {
        return dbService.getPlaygroundParserById(id);
    }

    /**
     * 保存TypeScript代码及其编译结果
     */
    @RouteMapping(value = "/typescript", method = RouteMethod.POST)
    public Future<JsonObject> saveTypeScriptCode(RoutingContext ctx) {
        Promise<JsonObject> promise = Promise.promise();

        try {
            JsonObject body = ctx.body().asJsonObject();
            Long parserId = body.getLong("parserId");
            String tsCode = body.getString("tsCode");
            String es5Code = body.getString("es5Code");
            String compileErrors = body.getString("compileErrors");
            String compilerVersion = body.getString("compilerVersion");
            String compileOptions = body.getString("compileOptions");
            Boolean isValid = body.getBoolean("isValid", true);

            if (parserId == null) {
                promise.complete(JsonResult.error("解析器ID不能为空").toJsonObject());
                return promise.future();
            }

            if (StringUtils.isBlank(tsCode)) {
                promise.complete(JsonResult.error("TypeScript代码不能为空").toJsonObject());
                return promise.future();
            }

            if (StringUtils.isBlank(es5Code)) {
                promise.complete(JsonResult.error("编译后的ES5代码不能为空").toJsonObject());
                return promise.future();
            }

            // 代码长度验证
            if (tsCode.length() > MAX_CODE_LENGTH || es5Code.length() > MAX_CODE_LENGTH) {
                promise.complete(JsonResult.error("代码长度超过限制（最大128KB）").toJsonObject());
                return promise.future();
            }

            JsonObject tsCodeInfo = new JsonObject();
            tsCodeInfo.put("parserId", parserId);
            tsCodeInfo.put("tsCode", tsCode);
            tsCodeInfo.put("es5Code", es5Code);
            tsCodeInfo.put("compileErrors", compileErrors);
            tsCodeInfo.put("compilerVersion", compilerVersion);
            tsCodeInfo.put("compileOptions", compileOptions);
            tsCodeInfo.put("isValid", isValid);
            tsCodeInfo.put("ip", getClientIp(ctx.request()));

            dbService.saveTypeScriptCode(tsCodeInfo).onSuccess(result -> {
                promise.complete(result);
            }).onFailure(e -> {
                log.error("保存TypeScript代码失败", e);
                promise.complete(JsonResult.error("保存失败: " + e.getMessage()).toJsonObject());
            });

        } catch (Exception e) {
            log.error("解析请求参数失败", e);
            promise.complete(JsonResult.error("解析请求参数失败: " + e.getMessage()).toJsonObject());
        }

        return promise.future();
    }

    /**
     * 根据parserId获取TypeScript代码
     */
    @RouteMapping(value = "/typescript/:parserId", method = RouteMethod.GET)
    public Future<JsonObject> getTypeScriptCode(Long parserId) {
        return dbService.getTypeScriptCodeByParserId(parserId);
    }

    /**
     * 更新TypeScript代码
     */
    @RouteMapping(value = "/typescript/:parserId", method = RouteMethod.PUT)
    public Future<JsonObject> updateTypeScriptCode(RoutingContext ctx, Long parserId) {
        Promise<JsonObject> promise = Promise.promise();

        try {
            JsonObject body = ctx.body().asJsonObject();
            String tsCode = body.getString("tsCode");
            String es5Code = body.getString("es5Code");
            String compileErrors = body.getString("compileErrors");
            String compilerVersion = body.getString("compilerVersion");
            String compileOptions = body.getString("compileOptions");
            Boolean isValid = body.getBoolean("isValid", true);

            if (StringUtils.isBlank(tsCode)) {
                promise.complete(JsonResult.error("TypeScript代码不能为空").toJsonObject());
                return promise.future();
            }

            if (StringUtils.isBlank(es5Code)) {
                promise.complete(JsonResult.error("编译后的ES5代码不能为空").toJsonObject());
                return promise.future();
            }

            // 代码长度验证
            if (tsCode.length() > MAX_CODE_LENGTH || es5Code.length() > MAX_CODE_LENGTH) {
                promise.complete(JsonResult.error("代码长度超过限制（最大128KB）").toJsonObject());
                return promise.future();
            }

            JsonObject tsCodeInfo = new JsonObject();
            tsCodeInfo.put("tsCode", tsCode);
            tsCodeInfo.put("es5Code", es5Code);
            tsCodeInfo.put("compileErrors", compileErrors);
            tsCodeInfo.put("compilerVersion", compilerVersion);
            tsCodeInfo.put("compileOptions", compileOptions);
            tsCodeInfo.put("isValid", isValid);

            dbService.updateTypeScriptCode(parserId, tsCodeInfo).onSuccess(result -> {
                promise.complete(result);
            }).onFailure(e -> {
                log.error("更新TypeScript代码失败", e);
                promise.complete(JsonResult.error("更新失败: " + e.getMessage()).toJsonObject());
            });

        } catch (Exception e) {
            log.error("解析请求参数失败", e);
            promise.complete(JsonResult.error("解析请求参数失败: " + e.getMessage()).toJsonObject());
        }

        return promise.future();
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServerRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.remoteAddress().host();
        }
        return ip;
    }

    /**
     * 获取异常堆栈信息
     */
    private String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}

