package cn.qaiu.parser.customjs;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * JavaScript演练场执行器
 * 用于临时执行JavaScript代码，不注册到解析器注册表
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class JsPlaygroundExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(JsPlaygroundExecutor.class);
    
    // JavaScript执行超时时间（秒）
    private static final long EXECUTION_TIMEOUT_SECONDS = 30;
    
    // 使用独立的线程池，不受Vert.x的BlockedThreadChecker监控
    private static final ExecutorService INDEPENDENT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r);
        thread.setName("playground-independent-" + System.currentTimeMillis());
        thread.setDaemon(true); // 设置为守护线程，服务关闭时自动清理
        return thread;
    });
    
    // 超时调度线程池，用于处理超时中断
    private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors.newScheduledThreadPool(2, r -> {
        Thread thread = new Thread(r);
        thread.setName("playground-timeout-scheduler-" + System.currentTimeMillis());
        thread.setDaemon(true);
        return thread;
    });
    
    private final ShareLinkInfo shareLinkInfo;
    private final String jsCode;
    private final ScriptEngine engine;
    private final JsHttpClient httpClient;
    private final JsPlaygroundLogger playgroundLogger;
    private final JsShareLinkInfoWrapper shareLinkInfoWrapper;
    private final JsFetchBridge fetchBridge;
    
    /**
     * 创建演练场执行器
     *
     * @param shareLinkInfo 分享链接信息
     * @param jsCode JavaScript代码
     */
    public JsPlaygroundExecutor(ShareLinkInfo shareLinkInfo, String jsCode) {
        this.shareLinkInfo = shareLinkInfo;
        this.jsCode = jsCode;
        
        // 检查是否有代理配置
        JsonObject proxyConfig = null;
        if (shareLinkInfo.getOtherParam().containsKey("proxy")) {
            proxyConfig = (JsonObject) shareLinkInfo.getOtherParam().get("proxy");
        }
        
        this.httpClient = new JsHttpClient(proxyConfig);
        this.playgroundLogger = new JsPlaygroundLogger();
        this.shareLinkInfoWrapper = new JsShareLinkInfoWrapper(shareLinkInfo);
        this.fetchBridge = new JsFetchBridge(httpClient);
        this.engine = initEngine();
    }
    
    /**
     * 初始化JavaScript引擎（带安全限制）
     */
    private ScriptEngine initEngine() {
        try {
            // 使用安全的ClassFilter创建Nashorn引擎
            NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
            
            // 正确的方法签名: getScriptEngine(String[] args, ClassLoader appLoader, ClassFilter classFilter)
            ScriptEngine engine = factory.getScriptEngine(new String[0], null, new SecurityClassFilter());
            
            if (engine == null) {
                throw new RuntimeException("无法创建JavaScript引擎，请确保Nashorn可用");
            }
            
            // 注入Java对象到JavaScript环境
            engine.put("http", httpClient);
            engine.put("logger", playgroundLogger);
            engine.put("shareLinkInfo", shareLinkInfoWrapper);
            engine.put("JavaFetch", fetchBridge);
            
            // 禁用Java对象访问
            engine.eval("var Java = undefined;");
            engine.eval("var JavaImporter = undefined;");
            engine.eval("var Packages = undefined;");
            engine.eval("var javax = undefined;");
            engine.eval("var org = undefined;");
            engine.eval("var com = undefined;");
            
            // 加载fetch运行时（Promise和fetch API polyfill）
            String fetchRuntime = JsParserExecutor.loadFetchRuntime();
            if (!fetchRuntime.isEmpty()) {
                engine.eval(fetchRuntime);
                playgroundLogger.infoJava("✅ Fetch API和Promise polyfill注入成功");
            }
            
            playgroundLogger.infoJava("🔒 安全的JavaScript引擎初始化成功（演练场）");
            
            // 执行JavaScript代码
            engine.eval(jsCode);
            
            log.debug("JavaScript引擎初始化成功（演练场）");
            return engine;
            
        } catch (Exception e) {
            log.error("JavaScript引擎初始化失败（演练场）", e);
            throw new RuntimeException("JavaScript引擎初始化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 执行parse方法（异步，带超时控制）
     * 使用独立线程池，不受Vert.x BlockedThreadChecker监控
     *
     * @return Future包装的执行结果
     */
    public Future<String> executeParseAsync() {
        Promise<String> promise = Promise.promise();
        
        // 使用独立的ExecutorService执行，避免Vert.x的BlockedThreadChecker输出警告
        CompletableFuture<String> executionFuture = CompletableFuture.supplyAsync(() -> {
            playgroundLogger.infoJava("开始执行parse方法");
            try {
                Object parseFunction = engine.get("parse");
                if (parseFunction == null) {
                    playgroundLogger.errorJava("JavaScript代码中未找到parse函数");
                    throw new RuntimeException("JavaScript代码中未找到parse函数");
                }
                
                if (parseFunction instanceof ScriptObjectMirror parseMirror) {
                    playgroundLogger.debugJava("调用parse函数");
                    log.debug("[JsPlaygroundExecutor] 调用parse函数，当前日志数量: {}", playgroundLogger.size());
                    Object result = parseMirror.call(null, shareLinkInfoWrapper, httpClient, playgroundLogger);
                    log.debug("[JsPlaygroundExecutor] parse函数执行完成，当前日志数量: {}", playgroundLogger.size());
                    
                    if (result instanceof String) {
                        playgroundLogger.infoJava("解析成功，返回结果: " + result);
                        return (String) result;
                    } else {
                        String errorMsg = "parse方法返回值类型错误，期望String，实际: " + 
                                (result != null ? result.getClass().getSimpleName() : "null");
                        playgroundLogger.errorJava(errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                } else {
                    playgroundLogger.errorJava("parse函数类型错误");
                    throw new RuntimeException("parse函数类型错误");
                }
            } catch (Exception e) {
                playgroundLogger.errorJava("执行parse方法失败: " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, INDEPENDENT_EXECUTOR);
        
        // 创建超时任务，强制取消执行
        ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(() -> {
            if (!executionFuture.isDone()) {
                executionFuture.cancel(true);  // 强制中断执行线程
                playgroundLogger.errorJava("执行超时，已强制中断");
                log.warn("JavaScript执行超时，已强制取消");
            }
        }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        // 处理执行结果
        executionFuture.whenComplete((result, error) -> {
            // 取消超时任务
            timeoutTask.cancel(false);
            
            if (error != null) {
                if (error instanceof CancellationException) {
                    String timeoutMsg = "JavaScript执行超时（超过" + EXECUTION_TIMEOUT_SECONDS + "秒），已强制中断";
                    playgroundLogger.errorJava(timeoutMsg);
                    log.error(timeoutMsg);
                    promise.fail(new RuntimeException(timeoutMsg));
                } else {
                    Throwable cause = error.getCause();
                    promise.fail(cause != null ? cause : error);
                }
            } else {
                promise.complete(result);
            }
        });
        
        return promise.future();
    }
    
    /**
     * 执行parseFileList方法（异步，带超时控制）
     * 使用独立线程池，不受Vert.x BlockedThreadChecker监控
     *
     * @return Future包装的文件列表
     */
    public Future<List<FileInfo>> executeParseFileListAsync() {
        Promise<List<FileInfo>> promise = Promise.promise();
        
        // 使用独立的ExecutorService执行，避免Vert.x的BlockedThreadChecker输出警告
        CompletableFuture<List<FileInfo>> executionFuture = CompletableFuture.supplyAsync(() -> {
            playgroundLogger.infoJava("开始执行parseFileList方法");
            try {
                Object parseFileListFunction = engine.get("parseFileList");
                if (parseFileListFunction == null) {
                    playgroundLogger.errorJava("JavaScript代码中未找到parseFileList函数");
                    throw new RuntimeException("JavaScript代码中未找到parseFileList函数");
                }
                
                if (parseFileListFunction instanceof ScriptObjectMirror parseFileListMirror) {
                    playgroundLogger.debugJava("调用parseFileList函数");
                    Object result = parseFileListMirror.call(null, shareLinkInfoWrapper, httpClient, playgroundLogger);
                    
                    if (result instanceof ScriptObjectMirror resultMirror) {
                        List<FileInfo> fileList = convertToFileInfoList(resultMirror);
                        playgroundLogger.infoJava("文件列表解析成功，共 " + fileList.size() + " 个文件");
                        return fileList;
                    } else {
                        String errorMsg = "parseFileList方法返回值类型错误，期望数组，实际: " + 
                                (result != null ? result.getClass().getSimpleName() : "null");
                        playgroundLogger.errorJava(errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                } else {
                    playgroundLogger.errorJava("parseFileList函数类型错误");
                    throw new RuntimeException("parseFileList函数类型错误");
                }
            } catch (Exception e) {
                playgroundLogger.errorJava("执行parseFileList方法失败: " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, INDEPENDENT_EXECUTOR);
        
        // 创建超时任务，强制取消执行
        ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(() -> {
            if (!executionFuture.isDone()) {
                executionFuture.cancel(true);  // 强制中断执行线程
                playgroundLogger.errorJava("执行超时，已强制中断");
                log.warn("JavaScript执行超时，已强制取消");
            }
        }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        // 处理执行结果
        executionFuture.whenComplete((result, error) -> {
            // 取消超时任务
            timeoutTask.cancel(false);
            
            if (error != null) {
                if (error instanceof CancellationException) {
                    String timeoutMsg = "JavaScript执行超时（超过" + EXECUTION_TIMEOUT_SECONDS + "秒），已强制中断";
                    playgroundLogger.errorJava(timeoutMsg);
                    log.error(timeoutMsg);
                    promise.fail(new RuntimeException(timeoutMsg));
                } else {
                    Throwable cause = error.getCause();
                    promise.fail(cause != null ? cause : error);
                }
            } else {
                promise.complete(result);
            }
        });
        
        return promise.future();
    }
    
    /**
     * 执行parseById方法（异步，带超时控制）
     * 使用独立线程池，不受Vert.x BlockedThreadChecker监控
     *
     * @return Future包装的执行结果
     */
    public Future<String> executeParseByIdAsync() {
        Promise<String> promise = Promise.promise();
        
        // 使用独立的ExecutorService执行，避免Vert.x的BlockedThreadChecker输出警告
        CompletableFuture<String> executionFuture = CompletableFuture.supplyAsync(() -> {
            playgroundLogger.infoJava("开始执行parseById方法");
            try {
                Object parseByIdFunction = engine.get("parseById");
                if (parseByIdFunction == null) {
                    playgroundLogger.errorJava("JavaScript代码中未找到parseById函数");
                    throw new RuntimeException("JavaScript代码中未找到parseById函数");
                }
                
                if (parseByIdFunction instanceof ScriptObjectMirror parseByIdMirror) {
                    playgroundLogger.debugJava("调用parseById函数");
                    Object result = parseByIdMirror.call(null, shareLinkInfoWrapper, httpClient, playgroundLogger);
                    
                    if (result instanceof String) {
                        playgroundLogger.infoJava("按ID解析成功: " + result);
                        return (String) result;
                    } else {
                        String errorMsg = "parseById方法返回值类型错误，期望String，实际: " + 
                                (result != null ? result.getClass().getSimpleName() : "null");
                        playgroundLogger.errorJava(errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                } else {
                    playgroundLogger.errorJava("parseById函数类型错误");
                    throw new RuntimeException("parseById函数类型错误");
                }
            } catch (Exception e) {
                playgroundLogger.errorJava("执行parseById方法失败: " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, INDEPENDENT_EXECUTOR);
        
        // 创建超时任务，强制取消执行
        ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(() -> {
            if (!executionFuture.isDone()) {
                executionFuture.cancel(true);  // 强制中断执行线程
                playgroundLogger.errorJava("执行超时，已强制中断");
                log.warn("JavaScript执行超时，已强制取消");
            }
        }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        // 处理执行结果
        executionFuture.whenComplete((result, error) -> {
            // 取消超时任务
            timeoutTask.cancel(false);
            
            if (error != null) {
                if (error instanceof CancellationException) {
                    String timeoutMsg = "JavaScript执行超时（超过" + EXECUTION_TIMEOUT_SECONDS + "秒），已强制中断";
                    playgroundLogger.errorJava(timeoutMsg);
                    log.error(timeoutMsg);
                    promise.fail(new RuntimeException(timeoutMsg));
                } else {
                    Throwable cause = error.getCause();
                    promise.fail(cause != null ? cause : error);
                }
            } else {
                promise.complete(result);
            }
        });
        
        return promise.future();
    }
    
    /**
     * 获取日志列表
     */
    public List<JsPlaygroundLogger.LogEntry> getLogs() {
        List<JsPlaygroundLogger.LogEntry> logs = playgroundLogger.getLogs();
        System.out.println("[JsPlaygroundExecutor] 获取日志，数量: " + logs.size());
        return logs;
    }
    
    /**
     * 获取ShareLinkInfo对象
     */
    public ShareLinkInfo getShareLinkInfo() {
        return shareLinkInfo;
    }
    
    /**
     * 将JavaScript对象数组转换为FileInfo列表
     */
    private List<FileInfo> convertToFileInfoList(ScriptObjectMirror resultMirror) {
        List<FileInfo> fileList = new ArrayList<>();
        
        if (resultMirror.isArray()) {
            for (int i = 0; i < resultMirror.size(); i++) {
                Object item = resultMirror.get(String.valueOf(i));
                if (item instanceof ScriptObjectMirror) {
                    FileInfo fileInfo = convertToFileInfo((ScriptObjectMirror) item);
                    if (fileInfo != null) {
                        fileList.add(fileInfo);
                    }
                }
            }
        }
        
        return fileList;
    }
    
    /**
     * 将JavaScript对象转换为FileInfo
     */
    private FileInfo convertToFileInfo(ScriptObjectMirror itemMirror) {
        try {
            FileInfo fileInfo = new FileInfo();
            
            // 设置基本字段
            if (itemMirror.hasMember("fileName")) {
                fileInfo.setFileName(itemMirror.getMember("fileName").toString());
            }
            if (itemMirror.hasMember("fileId")) {
                fileInfo.setFileId(itemMirror.getMember("fileId").toString());
            }
            if (itemMirror.hasMember("fileType")) {
                fileInfo.setFileType(itemMirror.getMember("fileType").toString());
            }
            if (itemMirror.hasMember("size")) {
                Object size = itemMirror.getMember("size");
                if (size instanceof Number) {
                    fileInfo.setSize(((Number) size).longValue());
                }
            }
            if (itemMirror.hasMember("sizeStr")) {
                fileInfo.setSizeStr(itemMirror.getMember("sizeStr").toString());
            }
            if (itemMirror.hasMember("createTime")) {
                fileInfo.setCreateTime(itemMirror.getMember("createTime").toString());
            }
            if (itemMirror.hasMember("updateTime")) {
                fileInfo.setUpdateTime(itemMirror.getMember("updateTime").toString());
            }
            if (itemMirror.hasMember("createBy")) {
                fileInfo.setCreateBy(itemMirror.getMember("createBy").toString());
            }
            if (itemMirror.hasMember("downloadCount")) {
                Object downloadCount = itemMirror.getMember("downloadCount");
                if (downloadCount instanceof Number) {
                    fileInfo.setDownloadCount(((Number) downloadCount).intValue());
                }
            }
            if (itemMirror.hasMember("fileIcon")) {
                fileInfo.setFileIcon(itemMirror.getMember("fileIcon").toString());
            }
            if (itemMirror.hasMember("panType")) {
                fileInfo.setPanType(itemMirror.getMember("panType").toString());
            }
            if (itemMirror.hasMember("parserUrl")) {
                fileInfo.setParserUrl(itemMirror.getMember("parserUrl").toString());
            }
            if (itemMirror.hasMember("previewUrl")) {
                fileInfo.setPreviewUrl(itemMirror.getMember("previewUrl").toString());
            }
            
            return fileInfo;
            
        } catch (Exception e) {
            playgroundLogger.errorJava("转换FileInfo对象失败", e);
            return null;
        }
    }
}

