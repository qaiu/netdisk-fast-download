package cn.qaiu.parser.custompy;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Python演练场执行器
 * 用于临时执行Python代码，不注册到解析器注册表
 * 使用独立线程池避免Vert.x BlockedThreadChecker警告
 *
 * @author QAIU
 */
public class PyPlaygroundExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(PyPlaygroundExecutor.class);
    
    // Python执行超时时间（秒）
    private static final long EXECUTION_TIMEOUT_SECONDS = 30;
    
    // 共享的GraalPy引擎
    private static final Engine SHARED_ENGINE = Engine.newBuilder()
            .option("engine.WarnInterpreterOnly", "false")
            .build();
    
    // 使用独立的线程池
    private static final ExecutorService INDEPENDENT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r);
        thread.setName("py-playground-independent-" + System.currentTimeMillis());
        thread.setDaemon(true);
        return thread;
    });
    
    // 超时调度线程池
    private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors.newScheduledThreadPool(2, r -> {
        Thread thread = new Thread(r);
        thread.setName("py-playground-timeout-scheduler-" + System.currentTimeMillis());
        thread.setDaemon(true);
        return thread;
    });
    
    private final ShareLinkInfo shareLinkInfo;
    private final String pyCode;
    private final PyHttpClient httpClient;
    private final PyPlaygroundLogger playgroundLogger;
    private final PyShareLinkInfoWrapper shareLinkInfoWrapper;
    private final PyCryptoUtils cryptoUtils;
    
    /**
     * 创建演练场执行器
     *
     * @param shareLinkInfo 分享链接信息
     * @param pyCode Python代码
     */
    public PyPlaygroundExecutor(ShareLinkInfo shareLinkInfo, String pyCode) {
        this.shareLinkInfo = shareLinkInfo;
        this.pyCode = pyCode;
        
        // 检查是否有代理配置
        JsonObject proxyConfig = null;
        if (shareLinkInfo.getOtherParam().containsKey("proxy")) {
            proxyConfig = (JsonObject) shareLinkInfo.getOtherParam().get("proxy");
        }
        
        this.httpClient = new PyHttpClient(proxyConfig);
        this.playgroundLogger = new PyPlaygroundLogger();
        this.shareLinkInfoWrapper = new PyShareLinkInfoWrapper(shareLinkInfo);
        this.cryptoUtils = new PyCryptoUtils();
    }
    
    /**
     * 创建安全的GraalPy Context
     */
    private Context createContext() {
        return Context.newBuilder("python")
                .engine(SHARED_ENGINE)
                .allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT)
                        .allowArrayAccess(true)
                        .allowListAccess(true)
                        .allowMapAccess(true)
                        .allowIterableAccess(true)
                        .allowIteratorAccess(true)
                        .build())
                .allowHostClassLookup(className -> false)
                .allowExperimentalOptions(true)
                .allowCreateThread(true)
                .allowNativeAccess(false)
                .allowCreateProcess(false)
                .allowIO(IOAccess.newBuilder()
                        .allowHostFileAccess(false)
                        .allowHostSocketAccess(false)
                        .build())
                .option("python.PythonHome", "")
                .option("python.ForceImportSite", "false")
                .build();
    }
    
    /**
     * 执行parse方法（异步，带超时控制）
     */
    public Future<String> executeParseAsync() {
        Promise<String> promise = Promise.promise();
        
        CompletableFuture<String> executionFuture = CompletableFuture.supplyAsync(() -> {
            playgroundLogger.infoJava("开始执行parse方法");
            
            try (Context context = createContext()) {
                // 注入Java对象到Python环境
                Value bindings = context.getBindings("python");
                bindings.putMember("http", httpClient);
                bindings.putMember("logger", playgroundLogger);
                bindings.putMember("share_link_info", shareLinkInfoWrapper);
                bindings.putMember("crypto", cryptoUtils);
                
                // 执行Python代码
                playgroundLogger.debugJava("执行Python代码");
                context.eval("python", pyCode);
                
                // 调用parse函数
                Value parseFunc = bindings.getMember("parse");
                if (parseFunc == null || !parseFunc.canExecute()) {
                    playgroundLogger.errorJava("Python代码中未找到parse函数");
                    throw new RuntimeException("Python代码中未找到parse函数");
                }
                
                playgroundLogger.debugJava("调用parse函数");
                Value result = parseFunc.execute(shareLinkInfoWrapper, httpClient, playgroundLogger);
                
                if (result.isString()) {
                    String downloadUrl = result.asString();
                    playgroundLogger.infoJava("解析成功，返回结果: " + downloadUrl);
                    return downloadUrl;
                } else {
                    String errorMsg = "parse方法返回值类型错误，期望String，实际: " + 
                            (result.isNull() ? "null" : result.getMetaObject().toString());
                    playgroundLogger.errorJava(errorMsg);
                    throw new RuntimeException(errorMsg);
                }
            } catch (Exception e) {
                playgroundLogger.errorJava("执行parse方法失败: " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, INDEPENDENT_EXECUTOR);
        
        // 创建超时任务
        ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(() -> {
            if (!executionFuture.isDone()) {
                executionFuture.cancel(true);
                playgroundLogger.errorJava("执行超时，已强制中断");
                log.warn("Python执行超时，已强制取消");
            }
        }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        // 处理执行结果
        executionFuture.whenComplete((result, error) -> {
            timeoutTask.cancel(false);
            
            if (error != null) {
                if (error instanceof CancellationException) {
                    String timeoutMsg = "Python执行超时（超过" + EXECUTION_TIMEOUT_SECONDS + "秒），已强制中断";
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
     */
    public Future<List<FileInfo>> executeParseFileListAsync() {
        Promise<List<FileInfo>> promise = Promise.promise();
        
        CompletableFuture<List<FileInfo>> executionFuture = CompletableFuture.supplyAsync(() -> {
            playgroundLogger.infoJava("开始执行parse_file_list方法");
            
            try (Context context = createContext()) {
                Value bindings = context.getBindings("python");
                bindings.putMember("http", httpClient);
                bindings.putMember("logger", playgroundLogger);
                bindings.putMember("share_link_info", shareLinkInfoWrapper);
                bindings.putMember("crypto", cryptoUtils);
                
                context.eval("python", pyCode);
                
                Value parseFileListFunc = bindings.getMember("parse_file_list");
                if (parseFileListFunc == null || !parseFileListFunc.canExecute()) {
                    playgroundLogger.errorJava("Python代码中未找到parse_file_list函数");
                    throw new RuntimeException("Python代码中未找到parse_file_list函数");
                }
                
                playgroundLogger.debugJava("调用parse_file_list函数");
                Value result = parseFileListFunc.execute(shareLinkInfoWrapper, httpClient, playgroundLogger);
                
                List<FileInfo> fileList = convertToFileInfoList(result);
                playgroundLogger.infoJava("文件列表解析成功，共 " + fileList.size() + " 个文件");
                return fileList;
            } catch (Exception e) {
                playgroundLogger.errorJava("执行parse_file_list方法失败: " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, INDEPENDENT_EXECUTOR);
        
        ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(() -> {
            if (!executionFuture.isDone()) {
                executionFuture.cancel(true);
                playgroundLogger.errorJava("执行超时，已强制中断");
            }
        }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        executionFuture.whenComplete((result, error) -> {
            timeoutTask.cancel(false);
            
            if (error != null) {
                if (error instanceof CancellationException) {
                    String timeoutMsg = "Python执行超时（超过" + EXECUTION_TIMEOUT_SECONDS + "秒），已强制中断";
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
     */
    public Future<String> executeParseByIdAsync() {
        Promise<String> promise = Promise.promise();
        
        CompletableFuture<String> executionFuture = CompletableFuture.supplyAsync(() -> {
            playgroundLogger.infoJava("开始执行parse_by_id方法");
            
            try (Context context = createContext()) {
                Value bindings = context.getBindings("python");
                bindings.putMember("http", httpClient);
                bindings.putMember("logger", playgroundLogger);
                bindings.putMember("share_link_info", shareLinkInfoWrapper);
                bindings.putMember("crypto", cryptoUtils);
                
                context.eval("python", pyCode);
                
                Value parseByIdFunc = bindings.getMember("parse_by_id");
                if (parseByIdFunc == null || !parseByIdFunc.canExecute()) {
                    playgroundLogger.errorJava("Python代码中未找到parse_by_id函数");
                    throw new RuntimeException("Python代码中未找到parse_by_id函数");
                }
                
                playgroundLogger.debugJava("调用parse_by_id函数");
                Value result = parseByIdFunc.execute(shareLinkInfoWrapper, httpClient, playgroundLogger);
                
                if (result.isString()) {
                    String downloadUrl = result.asString();
                    playgroundLogger.infoJava("按ID解析成功，返回结果: " + downloadUrl);
                    return downloadUrl;
                } else {
                    String errorMsg = "parse_by_id方法返回值类型错误";
                    playgroundLogger.errorJava(errorMsg);
                    throw new RuntimeException(errorMsg);
                }
            } catch (Exception e) {
                playgroundLogger.errorJava("执行parse_by_id方法失败: " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, INDEPENDENT_EXECUTOR);
        
        ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(() -> {
            if (!executionFuture.isDone()) {
                executionFuture.cancel(true);
                playgroundLogger.errorJava("执行超时，已强制中断");
            }
        }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        executionFuture.whenComplete((result, error) -> {
            timeoutTask.cancel(false);
            
            if (error != null) {
                if (error instanceof CancellationException) {
                    String timeoutMsg = "Python执行超时（超过" + EXECUTION_TIMEOUT_SECONDS + "秒），已强制中断";
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
    public List<PyPlaygroundLogger.LogEntry> getLogs() {
        return playgroundLogger.getLogs();
    }
    
    /**
     * 将Python列表转换为FileInfo列表
     */
    private List<FileInfo> convertToFileInfoList(Value result) {
        List<FileInfo> fileList = new ArrayList<>();
        
        if (result.hasArrayElements()) {
            long size = result.getArraySize();
            for (long i = 0; i < size; i++) {
                Value item = result.getArrayElement(i);
                FileInfo fileInfo = convertToFileInfo(item);
                if (fileInfo != null) {
                    fileList.add(fileInfo);
                }
            }
        }
        
        return fileList;
    }
    
    /**
     * 将Python字典转换为FileInfo
     */
    private FileInfo convertToFileInfo(Value item) {
        try {
            FileInfo fileInfo = new FileInfo();
            
            if (item.hasMember("file_name") || item.hasMember("fileName")) {
                Value val = item.hasMember("file_name") ? item.getMember("file_name") : item.getMember("fileName");
                if (val != null && !val.isNull()) {
                    fileInfo.setFileName(val.asString());
                }
            }
            if (item.hasMember("file_id") || item.hasMember("fileId")) {
                Value val = item.hasMember("file_id") ? item.getMember("file_id") : item.getMember("fileId");
                if (val != null && !val.isNull()) {
                    fileInfo.setFileId(val.asString());
                }
            }
            if (item.hasMember("file_type") || item.hasMember("fileType")) {
                Value val = item.hasMember("file_type") ? item.getMember("file_type") : item.getMember("fileType");
                if (val != null && !val.isNull()) {
                    fileInfo.setFileType(val.asString());
                }
            }
            if (item.hasMember("size")) {
                Value val = item.getMember("size");
                if (val != null && !val.isNull() && val.isNumber()) {
                    fileInfo.setSize(val.asLong());
                }
            }
            if (item.hasMember("pan_type") || item.hasMember("panType")) {
                Value val = item.hasMember("pan_type") ? item.getMember("pan_type") : item.getMember("panType");
                if (val != null && !val.isNull()) {
                    fileInfo.setPanType(val.asString());
                }
            }
            if (item.hasMember("parser_url") || item.hasMember("parserUrl")) {
                Value val = item.hasMember("parser_url") ? item.getMember("parser_url") : item.getMember("parserUrl");
                if (val != null && !val.isNull()) {
                    fileInfo.setParserUrl(val.asString());
                }
            }
            
            return fileInfo;
            
        } catch (Exception e) {
            playgroundLogger.errorJava("转换FileInfo对象失败: " + e.getMessage());
            return null;
        }
    }
}
