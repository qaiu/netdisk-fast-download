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
public class JsPlaygroundExecutor implements AutoCloseable {
    
    private static final Logger log = LoggerFactory.getLogger(JsPlaygroundExecutor.class);
    
    // JavaScript执行超时时间（秒）
    private static final long EXECUTION_TIMEOUT_SECONDS = 30;
    private static final int MAX_RESULT_STRING_LENGTH = 1024 * 1024;
    private static final int MAX_FILE_LIST_SIZE = 1000;
    private static final int MAX_FILE_FIELD_LENGTH = 4096;
    
    // 使用有界线程池，防止线程无限增长导致内存溢出
    private static final int POOL_MAX_THREADS = 16;
    private static final int POOL_QUEUE_CAPACITY = 256;
    private static final ExecutorService INDEPENDENT_EXECUTOR = new ThreadPoolExecutor(
            4, POOL_MAX_THREADS, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(POOL_QUEUE_CAPACITY),
            r -> {
                Thread thread = new Thread(r);
                thread.setName("playground-independent-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            },
            (r, executor) -> {
                // 拒绝策略：记录日志并抛出异常，避免阻塞 Vert.x EventLoop
                log.warn("演练场线程池已满，拒绝任务。活跃线程: {}, 队列大小: {}",
                        ((ThreadPoolExecutor) executor).getActiveCount(),
                        ((ThreadPoolExecutor) executor).getQueue().size());
                throw new java.util.concurrent.RejectedExecutionException("演练场线程池已满，请稍后重试");
            }
    );
    
    // 超时调度线程池，用于处理超时中断
    private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors.newScheduledThreadPool(2, r -> {
        Thread thread = new Thread(r);
        thread.setName("playground-timeout-scheduler-" + System.currentTimeMillis());
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 关闭静态线程池（应在应用关闭时调用）
     */
    public static void shutdownPools() {
        INDEPENDENT_EXECUTOR.shutdown();
        TIMEOUT_SCHEDULER.shutdown();
        log.info("JsPlaygroundExecutor 线程池已关闭");
    }
    
    private final ShareLinkInfo shareLinkInfo;
    private final String jsCode;
    private final ScriptEngine engine;
    private final JsHttpClient httpClient;
    private final JsPlaygroundLogger playgroundLogger;
    private final JsShareLinkInfoWrapper shareLinkInfoWrapper;
    private final JsFetchBridge fetchBridge;
    /** 标记是否已释放，防止重复关闭 */
    private volatile boolean closed = false;
    
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
        try {
            this.engine = initEngine();
        } catch (RuntimeException e) {
            this.httpClient.close();
            throw e;
        }
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
            
            playgroundLogger.infoJava("初始化成功");
            
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

        final CompletableFuture<String> executionFuture;
        try {
            // 使用独立的ExecutorService执行，避免Vert.x的BlockedThreadChecker输出警告
            executionFuture = CompletableFuture.supplyAsync(() -> {
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
                        String resultText = limitResultString((String) result, "parse");
                        playgroundLogger.infoJava("解析成功，返回结果长度: " + resultText.length());
                        return resultText;
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
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("演练场线程池已满，任务被拒绝");
            close(); // 释放已创建的 ScriptEngine 和 HttpClient 资源
            promise.fail(new RuntimeException("演练场线程池已满，请稍后重试", e));
            return promise.future();
        }

        // 创建超时任务。cancel(true) 只能请求中断，Nashorn 死循环不保证立即停止。
        ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(() -> {
            if (!executionFuture.isDone()) {
                executionFuture.cancel(true);
                playgroundLogger.errorJava("执行超时，已请求取消并释放资源");
                log.warn("JavaScript执行超时，已请求取消；Nashorn长循环可能继续占用线程");
            }
        }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // 处理执行结果
        executionFuture.whenComplete((result, error) -> {
            // 取消超时任务
            timeoutTask.cancel(false);

                if (error != null) {
                if (error instanceof CancellationException) {
                    String timeoutMsg = "JavaScript执行超时（超过" + EXECUTION_TIMEOUT_SECONDS + "秒），已返回超时并释放资源";
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

        final CompletableFuture<List<FileInfo>> executionFuture;
        try {
            // 使用独立的ExecutorService执行，避免Vert.x的BlockedThreadChecker输出警告
            executionFuture = CompletableFuture.supplyAsync(() -> {
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
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("演练场线程池已满，任务被拒绝");
            close(); // 释放已创建的 ScriptEngine 和 HttpClient 资源
            promise.fail(new RuntimeException("演练场线程池已满，请稍后重试", e));
            return promise.future();
        }

        // 创建超时任务。cancel(true) 只能请求中断，Nashorn 死循环不保证立即停止。
        ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(() -> {
            if (!executionFuture.isDone()) {
                executionFuture.cancel(true);
                playgroundLogger.errorJava("执行超时，已请求取消并释放资源");
                log.warn("JavaScript执行超时，已请求取消；Nashorn长循环可能继续占用线程");
            }
        }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // 处理执行结果
        executionFuture.whenComplete((result, error) -> {
            // 取消超时任务
            timeoutTask.cancel(false);

                if (error != null) {
                if (error instanceof CancellationException) {
                    String timeoutMsg = "JavaScript执行超时（超过" + EXECUTION_TIMEOUT_SECONDS + "秒），已返回超时并释放资源";
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

        final CompletableFuture<String> executionFuture;
        try {
            // 使用独立的ExecutorService执行，避免Vert.x的BlockedThreadChecker输出警告
            executionFuture = CompletableFuture.supplyAsync(() -> {
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
                        String resultText = limitResultString((String) result, "parseById");
                        playgroundLogger.infoJava("按ID解析成功，返回结果长度: " + resultText.length());
                        return resultText;
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
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("演练场线程池已满，任务被拒绝");
            close(); // 释放已创建的 ScriptEngine 和 HttpClient 资源
            promise.fail(new RuntimeException("演练场线程池已满，请稍后重试", e));
            return promise.future();
        }

        // 创建超时任务。cancel(true) 只能请求中断，Nashorn 死循环不保证立即停止。
        ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(() -> {
            if (!executionFuture.isDone()) {
                executionFuture.cancel(true);
                playgroundLogger.errorJava("执行超时，已请求取消并释放资源");
                log.warn("JavaScript执行超时，已请求取消；Nashorn长循环可能继续占用线程");
            }
        }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // 处理执行结果
        executionFuture.whenComplete((result, error) -> {
            // 取消超时任务
            timeoutTask.cancel(false);

                if (error != null) {
                if (error instanceof CancellationException) {
                    String timeoutMsg = "JavaScript执行超时（超过" + EXECUTION_TIMEOUT_SECONDS + "秒），已返回超时并释放资源";
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
        log.debug("获取日志，数量: {}", logs.size());
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
            if (resultMirror.size() > MAX_FILE_LIST_SIZE) {
                throw new RuntimeException("文件列表数量超过限制: " + resultMirror.size());
            }
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
                fileInfo.setFileName(limitField(itemMirror.getMember("fileName")));
            }
            if (itemMirror.hasMember("fileId")) {
                fileInfo.setFileId(limitField(itemMirror.getMember("fileId")));
            }
            if (itemMirror.hasMember("fileType")) {
                fileInfo.setFileType(limitField(itemMirror.getMember("fileType")));
            }
            if (itemMirror.hasMember("size")) {
                Object size = itemMirror.getMember("size");
                if (size instanceof Number) {
                    fileInfo.setSize(((Number) size).longValue());
                }
            }
            if (itemMirror.hasMember("sizeStr")) {
                fileInfo.setSizeStr(limitField(itemMirror.getMember("sizeStr")));
            }
            if (itemMirror.hasMember("createTime")) {
                fileInfo.setCreateTime(limitField(itemMirror.getMember("createTime")));
            }
            if (itemMirror.hasMember("updateTime")) {
                fileInfo.setUpdateTime(limitField(itemMirror.getMember("updateTime")));
            }
            if (itemMirror.hasMember("createBy")) {
                fileInfo.setCreateBy(limitField(itemMirror.getMember("createBy")));
            }
            if (itemMirror.hasMember("downloadCount")) {
                Object downloadCount = itemMirror.getMember("downloadCount");
                if (downloadCount instanceof Number) {
                    fileInfo.setDownloadCount(((Number) downloadCount).intValue());
                }
            }
            if (itemMirror.hasMember("fileIcon")) {
                fileInfo.setFileIcon(limitField(itemMirror.getMember("fileIcon")));
            }
            if (itemMirror.hasMember("panType")) {
                fileInfo.setPanType(limitField(itemMirror.getMember("panType")));
            }
            if (itemMirror.hasMember("parserUrl")) {
                fileInfo.setParserUrl(limitField(itemMirror.getMember("parserUrl")));
            }
            if (itemMirror.hasMember("previewUrl")) {
                fileInfo.setPreviewUrl(limitField(itemMirror.getMember("previewUrl")));
            }
            
            return fileInfo;

        } catch (Exception e) {
            playgroundLogger.errorJava("转换FileInfo对象失败", e);
            return null;
        }
    }

    private static String limitResultString(String value, String operation) {
        if (value.length() > MAX_RESULT_STRING_LENGTH) {
            throw new RuntimeException(operation + " 返回结果过大: " + value.length() + " 字符");
        }
        return value;
    }

    private static String limitField(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if (text.length() > MAX_FILE_FIELD_LENGTH) {
            throw new RuntimeException("文件字段过长: " + text.length() + " 字符");
        }
        return text;
    }

    /**
     * 释放资源（HttpClient 和 ScriptEngine），避免内存泄漏
     * 幂等：可安全多次调用
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (httpClient != null) {
            httpClient.close();
        }
        // 清除 ScriptEngine 的所有 bindings，彻底释放资源
        if (engine != null) {
            try {
                // 清除注入的 Java 对象引用
                engine.put("http", null);
                engine.put("logger", null);
                engine.put("shareLinkInfo", null);
                engine.put("JavaFetch", null);
                // 清除所有 ENGINE_SCOPE bindings，包括 eval 加载的 JS 函数
                var bindings = engine.getBindings(javax.script.ScriptContext.ENGINE_SCOPE);
                if (bindings != null) {
                    bindings.clear();
                }
            } catch (Exception e) {
                log.warn("清理 ScriptEngine bindings 失败: {}", e.getMessage());
            }
        }
        log.debug("JsPlaygroundExecutor 资源已释放");
    }
}

