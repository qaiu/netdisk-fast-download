package cn.qaiu.parser.custompy;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Python演练场执行器
 * 用于临时执行Python代码，不注册到解析器注册表
 * 使用独立线程池避免Vert.x BlockedThreadChecker警告
 * 使用 PyContextPool 进行 Engine 和 Context 池化管理
 *
 * @author QAIU
 */
public class PyPlaygroundExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(PyPlaygroundExecutor.class);
    
    // Python执行超时时间（秒）
    private static final long EXECUTION_TIMEOUT_SECONDS = 30;
    
    // Context池实例
    private static final PyContextPool CONTEXT_POOL = PyContextPool.getInstance();
    
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
     * 执行parse方法（异步，带超时控制）
     */
    public Future<String> executeParseAsync() {
        Promise<String> promise = Promise.promise();
        
        // 在执行前进行安全检查
        PyCodeSecurityChecker.SecurityCheckResult securityResult = PyCodeSecurityChecker.check(pyCode);
        if (!securityResult.isPassed()) {
            playgroundLogger.errorJava("安全检查失败: " + securityResult.getMessage());
            promise.fail(new SecurityException("代码安全检查失败: " + securityResult.getMessage()));
            return promise.future();
        }
        playgroundLogger.debugJava("安全检查通过");
        
        // Python代码预处理 - 检测并注入猴子补丁
        PyCodePreprocessor.PyPreprocessResult preprocessResult = PyCodePreprocessor.preprocess(pyCode);
        playgroundLogger.infoJava(preprocessResult.getLogMessage());
        String codeToExecute = preprocessResult.getProcessedCode();
        
        CompletableFuture<String> executionFuture = CompletableFuture.supplyAsync(() -> {
            playgroundLogger.infoJava("开始执行parse方法");
            
            // 使用池化的 Context，自动归还
            try (PyContextPool.PooledContext pc = CONTEXT_POOL.acquire()) {
                Context context = pc.getContext();
                // 注入Java对象到Python环境
                Value bindings = context.getBindings("python");
                bindings.putMember("http", httpClient);
                bindings.putMember("logger", playgroundLogger);
                bindings.putMember("share_link_info", shareLinkInfoWrapper);
                bindings.putMember("crypto", cryptoUtils);
                
                // 执行Python代码（已支持真正的 pip 包如 requests, zlib 等）
                playgroundLogger.debugJava("执行Python代码");
                context.eval("python", codeToExecute);
                
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
            } catch (PolyglotException e) {
                // 处理 Python 语法错误和运行时错误
                String errorMsg = formatPolyglotException(e);
                playgroundLogger.errorJava("执行parse方法失败: " + errorMsg);
                throw new RuntimeException(errorMsg, e);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = e.getClass().getName();
                    if (e.getCause() != null) {
                        errorMsg += ": " + (e.getCause().getMessage() != null ? 
                                e.getCause().getMessage() : e.getCause().getClass().getName());
                    }
                }
                playgroundLogger.errorJava("执行parse方法失败: " + errorMsg, e);
                throw new RuntimeException(errorMsg, e);
            }
        }, CONTEXT_POOL.getPythonExecutor());
        
        // 创建超时任务
        ScheduledFuture<?> timeoutTask = CONTEXT_POOL.getTimeoutScheduler().schedule(() -> {
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
        
        // Python代码预处理 - 检测并注入猴子补丁
        PyCodePreprocessor.PyPreprocessResult preprocessResult = PyCodePreprocessor.preprocess(pyCode);
        playgroundLogger.infoJava(preprocessResult.getLogMessage());
        String codeToExecute = preprocessResult.getProcessedCode();
        
        CompletableFuture<List<FileInfo>> executionFuture = CompletableFuture.supplyAsync(() -> {
            playgroundLogger.infoJava("开始执行parse_file_list方法");
            
            // 使用池化的 Context，自动归还
            try (PyContextPool.PooledContext pc = CONTEXT_POOL.acquire()) {
                Context context = pc.getContext();
                Value bindings = context.getBindings("python");
                bindings.putMember("http", httpClient);
                bindings.putMember("logger", playgroundLogger);
                bindings.putMember("share_link_info", shareLinkInfoWrapper);
                bindings.putMember("crypto", cryptoUtils);
                
                // 执行Python代码（已支持真正的 pip 包）
                context.eval("python", codeToExecute);
                
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
            } catch (PolyglotException e) {
                // 处理 Python 语法错误和运行时错误
                String errorMsg = formatPolyglotException(e);
                playgroundLogger.errorJava("执行parse_file_list方法失败: " + errorMsg);
                throw new RuntimeException(errorMsg, e);
            } catch (Exception e) {
                playgroundLogger.errorJava("执行parse_file_list方法失败: " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, CONTEXT_POOL.getPythonExecutor());
        
        ScheduledFuture<?> timeoutTask = CONTEXT_POOL.getTimeoutScheduler().schedule(() -> {
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
        
        // Python代码预处理 - 检测并注入猴子补丁
        PyCodePreprocessor.PyPreprocessResult preprocessResult = PyCodePreprocessor.preprocess(pyCode);
        playgroundLogger.infoJava(preprocessResult.getLogMessage());
        String codeToExecute = preprocessResult.getProcessedCode();
        
        CompletableFuture<String> executionFuture = CompletableFuture.supplyAsync(() -> {
            playgroundLogger.infoJava("开始执行parse_by_id方法");
            
            // 使用池化的 Context，自动归还
            try (PyContextPool.PooledContext pc = CONTEXT_POOL.acquire()) {
                Context context = pc.getContext();
                Value bindings = context.getBindings("python");
                bindings.putMember("http", httpClient);
                bindings.putMember("logger", playgroundLogger);
                bindings.putMember("share_link_info", shareLinkInfoWrapper);
                bindings.putMember("crypto", cryptoUtils);
                
                // 执行Python代码（已支持真正的 pip 包）
                context.eval("python", codeToExecute);
                
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
        }, CONTEXT_POOL.getPythonExecutor());
        
        ScheduledFuture<?> timeoutTask = CONTEXT_POOL.getTimeoutScheduler().schedule(() -> {
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
    
    /**
     * 格式化 PolyglotException 异常信息，提取详细的错误位置和描述
     */
    private String formatPolyglotException(PolyglotException e) {
        StringBuilder sb = new StringBuilder();
        
        // 判断是否为语法错误
        if (e.isSyntaxError()) {
            sb.append("Python语法错误: ");
        } else if (e.isGuestException()) {
            sb.append("Python运行时错误: ");
        } else {
            sb.append("Python执行错误: ");
        }
        
        // 添加错误消息
        String message = e.getMessage();
        if (message != null && !message.isEmpty()) {
            sb.append(message);
        }
        
        // 添加源代码位置信息
        if (e.getSourceLocation() != null) {
            org.graalvm.polyglot.SourceSection sourceSection = e.getSourceLocation();
            sb.append("\n位置: ");
            
            // 文件名（如果有）
            if (sourceSection.getSource() != null && sourceSection.getSource().getName() != null) {
                sb.append(sourceSection.getSource().getName()).append(", ");
            }
            
            // 行号和列号
            sb.append("第 ").append(sourceSection.getStartLine()).append(" 行");
            if (sourceSection.hasColumns()) {
                sb.append(", 第 ").append(sourceSection.getStartColumn()).append(" 列");
            }
            
            // 显示出错的代码行（如果可用）
            if (sourceSection.hasCharIndex() && sourceSection.getCharacters() != null) {
                sb.append("\n错误代码: ").append(sourceSection.getCharacters().toString().trim());
            }
        }
        
        // 添加堆栈跟踪（仅显示Python部分）
        if (e.isGuestException() && e.getPolyglotStackTrace() != null) {
            sb.append("\n\nPython堆栈跟踪:");
            boolean foundPythonFrame = false;
            for (PolyglotException.StackFrame frame : e.getPolyglotStackTrace()) {
                if (frame.isGuestFrame() && frame.getLanguage() != null && 
                    frame.getLanguage().getId().equals("python")) {
                    foundPythonFrame = true;
                    sb.append("\n  at ").append(frame.getRootName() != null ? frame.getRootName() : "<unknown>");
                    if (frame.getSourceLocation() != null) {
                        org.graalvm.polyglot.SourceSection loc = frame.getSourceLocation();
                        sb.append(" (");
                        if (loc.getSource() != null && loc.getSource().getName() != null) {
                            sb.append(loc.getSource().getName()).append(":");
                        }
                        sb.append("line ").append(loc.getStartLine()).append(")");
                    }
                }
            }
            if (!foundPythonFrame) {
                sb.append("\n  (无Python堆栈信息)");
            }
        }
        
        return sb.toString();
    }
}
