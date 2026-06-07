package cn.qaiu.parser.customjs;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.custom.CustomParserConfig;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonObject;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * JavaScript解析器执行器
 * 实现IPanTool接口，执行JavaScript解析器逻辑
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/17
 */
public class JsParserExecutor implements IPanTool, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JsParserExecutor.class);

    private static volatile WorkerExecutor EXECUTOR;
    private static final Object EXECUTOR_LOCK = new Object();
    private static volatile boolean executorShutdown = false;

    /** 安全网调度器：当 onComplete 未触发时，延迟强制释放资源 */
    private static final ScheduledExecutorService CLEANUP_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "js-parser-cleanup-safety");
                t.setDaemon(true);
                return t;
            });
    private static final long EXECUTION_TIMEOUT_SECONDS = 30;
    private static final int MAX_RESULT_STRING_LENGTH = 1024 * 1024;
    private static final int MAX_FILE_LIST_SIZE = 1000;
    private static final int MAX_FILE_FIELD_LENGTH = 4096;
    private static final int MAX_CONCURRENT_EXECUTIONS =
            Math.max(1, Integer.getInteger("parser.custom.js.maxConcurrentExecutions", 32));
    private static final Semaphore EXECUTION_PERMITS = new Semaphore(MAX_CONCURRENT_EXECUTIONS);

    private static volatile String FETCH_RUNTIME_JS = null;
    
    private final CustomParserConfig config;
    private final ShareLinkInfo shareLinkInfo;
    private volatile ScriptEngine engine;
    private final Object engineLock = new Object();
    private final JsHttpClient httpClient;
    private final JsLogger jsLogger;
    private final JsShareLinkInfoWrapper shareLinkInfoWrapper;
    private final JsFetchBridge fetchBridge;
    /** 标记是否已释放，防止重复关闭 */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object lifecycleLock = new Object();
    private volatile boolean running = false;
    private volatile boolean closeRequested = false;
    /** 安全网定时任务句柄，正常完成时取消 */
    private volatile ScheduledFuture<?> safetyCleanupFuture = null;
    
    public JsParserExecutor(ShareLinkInfo shareLinkInfo, CustomParserConfig config) {
        this.config = config;
        this.shareLinkInfo = shareLinkInfo;
        
        // 检查是否有代理配置
        JsonObject proxyConfig = null;
        if (shareLinkInfo.getOtherParam().containsKey("proxy")) {
            proxyConfig = (JsonObject) shareLinkInfo.getOtherParam().get("proxy");
        }
        
        this.httpClient = new JsHttpClient(proxyConfig);
        this.jsLogger = new JsLogger("JsParser-" + config.getType());
        this.shareLinkInfoWrapper = new JsShareLinkInfoWrapper(shareLinkInfo);
        this.fetchBridge = new JsFetchBridge(httpClient);
    }
    
    /**
     * 加载fetch运行时JS代码
     * @return fetch运行时代码
     */
    static String loadFetchRuntime() {
        if (FETCH_RUNTIME_JS != null) {
            return FETCH_RUNTIME_JS;
        }
        
        try (InputStream is = JsParserExecutor.class.getClassLoader().getResourceAsStream("fetch-runtime.js")) {
            if (is == null) {
                log.warn("未找到fetch-runtime.js文件，fetch API将不可用");
                return "";
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                FETCH_RUNTIME_JS = reader.lines().collect(Collectors.joining("\n"));
                log.debug("Fetch运行时加载成功，大小: {} 字符", FETCH_RUNTIME_JS.length());
                return FETCH_RUNTIME_JS;
            }
        } catch (Exception e) {
            log.error("加载fetch-runtime.js失败", e);
            return "";
        }
    }
    
    /**
     * 获取ShareLinkInfo对象
     * @return ShareLinkInfo对象
     */
    @Override
    public ShareLinkInfo getShareLinkInfo() {
        return shareLinkInfo;
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
            this.engine = engine;
            
            // 注入Java对象到JavaScript环境
            engine.put("http", httpClient);
            engine.put("logger", jsLogger);
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
            String fetchRuntime = loadFetchRuntime();
            if (!fetchRuntime.isEmpty()) {
                engine.eval(fetchRuntime);
                log.debug("✅ Fetch API和Promise polyfill注入成功");
            }
            
            log.debug("🔒 安全的JavaScript引擎初始化成功，解析器类型: {}", config.getType());
            
            // 执行JavaScript代码
            engine.eval(config.getJsCode());
            
            return engine;
            
        } catch (Exception e) {
            log.error("JavaScript引擎初始化失败", e);
            throw new RuntimeException("JavaScript引擎初始化失败: " + e.getMessage(), e);
        }
    }

    private ScriptEngine engine() {
        ScriptEngine current = engine;
        if (current != null) {
            return current;
        }
        synchronized (engineLock) {
            if (closed.get()) {
                throw new IllegalStateException("JavaScript解析器已关闭");
            }
            if (engine == null) {
                engine = initEngine();
            }
            return engine;
        }
    }

    private void beginExecution() {
        synchronized (lifecycleLock) {
            if (closed.get() || closeRequested) {
                throw new IllegalStateException("JavaScript解析器已关闭");
            }
            if (running) {
                throw new IllegalStateException("JavaScript解析器已在运行");
            }
            running = true;
        }
    }

    private void finishExecution() {
        synchronized (lifecycleLock) {
            running = false;
            if (closeRequested) {
                doClose();
            }
        }
    }
    
    /**
     * 释放资源（ScriptEngine 和 HttpClient），避免内存泄漏
     * 幂等：可安全多次调用
     */
    @Override
    public void close() {
        synchronized (lifecycleLock) {
            closeRequested = true;
            cancelSafetyCleanup();
            if (running || closed.get()) {
                closeExternalResources();
                return;
            }
            doClose();
        }
    }

    private void doClose() {
        if (!closed.compareAndSet(false, true)) return;
        closeRequested = false;
        closeExternalResources();
        cleanupEngine();
    }

    private void closeExternalResources() {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    private void cleanupEngine() {
        // 清除 ScriptEngine 持有的所有引用和内部状态，帮助 GC 回收
        if (engine != null) {
            try {
                engine.put("http", null);
                engine.put("logger", null);
                engine.put("shareLinkInfo", null);
                engine.put("JavaFetch", null);
                // 彻底清除 ENGINE_SCOPE bindings，释放 JS AST、编译函数、闭包等运行时状态
                var bindings = engine.getBindings(javax.script.ScriptContext.ENGINE_SCOPE);
                if (bindings != null) {
                    bindings.clear();
                }
            } catch (Exception e) {
                log.warn("清理 ScriptEngine bindings 失败: {}", e.getMessage());
            }
        }
    }

    private void cancelSafetyCleanup() {
        // 取消安全网定时任务（如果正常完成则无需再触发）
        if (safetyCleanupFuture != null) {
            safetyCleanupFuture.cancel(false);
            safetyCleanupFuture = null;
        }
    }

    /**
     * 关闭全局 WorkerExecutor 和清理调度器（应在应用关闭时调用）
     */
    public static void shutdownExecutor() {
        synchronized (EXECUTOR_LOCK) {
            executorShutdown = true;
            if (EXECUTOR != null) {
                EXECUTOR.close();
                EXECUTOR = null;
                log.info("JsParserExecutor WorkerExecutor 已关闭");
            }
        }
        CLEANUP_SCHEDULER.shutdown();
    }

    /**
     * 获取或创建 WorkerExecutor（懒加载）
     */
    private static WorkerExecutor getExecutor() {
        synchronized (EXECUTOR_LOCK) {
            if (executorShutdown) {
                throw new IllegalStateException("JavaScript解析器 WorkerExecutor 已关闭");
            }
            if (EXECUTOR == null) {
                EXECUTOR = WebClientVertxInit.get().createSharedWorkerExecutor("parser-executor", 32);
            }
            return EXECUTOR;
        }
    }

    private <T> Future<T> executeBlockingWithPermit(String operation, Callable<T> blockingCode) {
        if (!EXECUTION_PERMITS.tryAcquire()) {
            String message = "JavaScript " + operation + " 执行并发已满，请稍后重试";
            jsLogger.error(message);
            close();
            return Future.failedFuture(message);
        }

        try {
            return getExecutor().executeBlocking(() -> {
                boolean executionStarted = false;
                try {
                    beginExecution();
                    executionStarted = true;
                    return blockingCode.call();
                } finally {
                    if (executionStarted) {
                        finishExecution();
                    }
                    EXECUTION_PERMITS.release();
                }
            });
        } catch (Throwable e) {
            EXECUTION_PERMITS.release();
            close();
            return Future.failedFuture(e);
        }
    }

    private <T> Future<T> withTimeout(Future<T> executionFuture, String operation) {
        Promise<T> promise = Promise.promise();
        try {
            safetyCleanupFuture = CLEANUP_SCHEDULER.schedule(() -> {
                if (promise.tryFail("JavaScript " + operation + " 执行超时（" + EXECUTION_TIMEOUT_SECONDS + "秒）")) {
                    jsLogger.error("{} 执行超时，已停止外部HTTP资源；ScriptEngine将在执行线程退出后清理", operation);
                    close();
                }
            }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("安全网调度失败: {}", e.getMessage());
        }
        executionFuture.onComplete(ar -> {
            cancelSafetyCleanup();
            if (ar.succeeded()) {
                promise.tryComplete(ar.result());
            } else {
                promise.tryFail(ar.cause());
            }
            close();
        });
        return promise.future();
    }

    @Override
    public Future<String> parse() {
        jsLogger.info("开始执行JavaScript解析器: {}", config.getType());

        // 使用executeBlocking在工作线程上执行，避免阻塞EventLoop线程
        Future<String> executionFuture = executeBlockingWithPermit("parse", () -> {
            ScriptEngine engine = engine();
            // 直接调用全局parse函数
            Object parseFunction = engine.get("parse");
            if (parseFunction == null) {
                throw new RuntimeException("JavaScript代码中未找到parse函数");
            }

            if (parseFunction instanceof ScriptObjectMirror parseMirror) {

                Object result = parseMirror.call(null, shareLinkInfoWrapper, httpClient, jsLogger);

                if (result instanceof String) {
                    String resultText = limitResultString((String) result, "parse");
                    jsLogger.info("解析成功，结果长度: {}", resultText.length());
                    return resultText;
                } else {
                    jsLogger.error("parse方法返回值类型错误，期望String，实际: {}",
                            result != null ? result.getClass().getSimpleName() : "null");
                    throw new RuntimeException("parse方法返回值类型错误");
                }
            } else {
                throw new RuntimeException("parse函数类型错误");
            }
        });
        return withTimeout(executionFuture, "parse");
    }
    
    @Override
    public Future<List<FileInfo>> parseFileList() {
        jsLogger.info("开始执行JavaScript文件列表解析: {}", config.getType());

        // 使用executeBlocking在工作线程上执行，避免阻塞EventLoop线程
        Future<List<FileInfo>> executionFuture = executeBlockingWithPermit("parseFileList", () -> {
            ScriptEngine engine = engine();
            // 直接调用全局parseFileList函数
            Object parseFileListFunction = engine.get("parseFileList");
            if (parseFileListFunction == null) {
                throw new RuntimeException("JavaScript代码中未找到parseFileList函数");
            }

            // 调用parseFileList方法
            if (parseFileListFunction instanceof ScriptObjectMirror parseFileListMirror) {

                Object result = parseFileListMirror.call(null, shareLinkInfoWrapper, httpClient, jsLogger);

                if (result instanceof ScriptObjectMirror resultMirror) {
                    List<FileInfo> fileList = convertToFileInfoList(resultMirror);

                    jsLogger.info("文件列表解析成功，共 {} 个文件", fileList.size());
                    return fileList;
                } else {
                    jsLogger.error("parseFileList方法返回值类型错误，期望数组，实际: {}",
                            result != null ? result.getClass().getSimpleName() : "null");
                    throw new RuntimeException("parseFileList方法返回值类型错误");
                }
            } else {
                throw new RuntimeException("parseFileList函数类型错误");
            }
        });
        return withTimeout(executionFuture, "parseFileList");
    }
    
    @Override
    public Future<String> parseById() {
        jsLogger.info("开始执行JavaScript按ID解析: {}", config.getType());

        // 使用executeBlocking在工作线程上执行，避免阻塞EventLoop线程
        Future<String> executionFuture = executeBlockingWithPermit("parseById", () -> {
            ScriptEngine engine = engine();
            // 直接调用全局parseById函数
            Object parseByIdFunction = engine.get("parseById");
            if (parseByIdFunction == null) {
                throw new RuntimeException("JavaScript代码中未找到parseById函数");
            }

            // 调用parseById方法
            if (parseByIdFunction instanceof ScriptObjectMirror parseByIdMirror) {

                Object result = parseByIdMirror.call(null, shareLinkInfoWrapper, httpClient, jsLogger);

                if (result instanceof String) {
                    String resultText = limitResultString((String) result, "parseById");
                    jsLogger.info("按ID解析成功，结果长度: {}", resultText.length());
                    return resultText;
                } else {
                    jsLogger.error("parseById方法返回值类型错误，期望String，实际: {}",
                            result != null ? result.getClass().getSimpleName() : "null");
                    throw new RuntimeException("parseById方法返回值类型错误");
                }
            } else {
                throw new RuntimeException("parseById函数类型错误");
            }
        });
        return withTimeout(executionFuture, "parseById");
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
            jsLogger.error("转换FileInfo对象失败", e);
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
}
