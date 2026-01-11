package cn.qaiu.parser.custompy;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.custom.CustomParserConfig;
import io.vertx.core.Future;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonObject;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Python解析器执行器
 * 使用GraalPy执行Python解析器脚本
 * 实现IPanTool接口，执行Python解析器逻辑
 * 使用 PyContextPool 进行 Engine 池化管理
 *
 * @author QAIU
 */
public class PyParserExecutor implements IPanTool {
    
    private static final Logger log = LoggerFactory.getLogger(PyParserExecutor.class);
    
    private static final WorkerExecutor EXECUTOR = WebClientVertxInit.get()
            .createSharedWorkerExecutor("py-parser-executor", 32);
    
    // Context池实例
    private static final PyContextPool CONTEXT_POOL = PyContextPool.getInstance();
    
    private final CustomParserConfig config;
    private final ShareLinkInfo shareLinkInfo;
    private final PyHttpClient httpClient;
    private final PyLogger pyLogger;
    private final PyShareLinkInfoWrapper shareLinkInfoWrapper;
    private final PyCryptoUtils cryptoUtils;
    
    public PyParserExecutor(ShareLinkInfo shareLinkInfo, CustomParserConfig config) {
        this.config = config;
        this.shareLinkInfo = shareLinkInfo;
        
        // 检查是否有代理配置
        JsonObject proxyConfig = null;
        if (shareLinkInfo.getOtherParam().containsKey("proxy")) {
            proxyConfig = (JsonObject) shareLinkInfo.getOtherParam().get("proxy");
        }
        
        this.httpClient = new PyHttpClient(proxyConfig);
        this.pyLogger = new PyLogger("PyParser-" + config.getType());
        this.shareLinkInfoWrapper = new PyShareLinkInfoWrapper(shareLinkInfo);
        this.cryptoUtils = new PyCryptoUtils();
    }
    
    /**
     * 获取ShareLinkInfo对象
     * @return ShareLinkInfo对象
     */
    @Override
    public ShareLinkInfo getShareLinkInfo() {
        return shareLinkInfo;
    }
    
    @Override
    public Future<String> parse() {
        pyLogger.info("开始执行Python解析器: {}", config.getType());
        
        return EXECUTOR.executeBlocking(() -> {
            // 使用池化的 Context，自动归还
            try (PyContextPool.PooledContext pc = CONTEXT_POOL.acquire()) {
                Context context = pc.getContext();
                // 注入Java对象到Python环境
                Value bindings = context.getBindings("python");
                bindings.putMember("http", httpClient);
                bindings.putMember("logger", pyLogger);
                bindings.putMember("share_link_info", shareLinkInfoWrapper);
                bindings.putMember("crypto", cryptoUtils);
                
                // 执行Python代码（已支持真正的 pip 包如 requests, zlib 等）
                context.eval("python", config.getPyCode());
                
                // 调用parse函数
                Value parseFunc = bindings.getMember("parse");
                if (parseFunc == null || !parseFunc.canExecute()) {
                    throw new RuntimeException("Python代码中未找到parse函数");
                }
                
                Value result = parseFunc.execute(shareLinkInfoWrapper, httpClient, pyLogger);
                
                if (result.isString()) {
                    String downloadUrl = result.asString();
                    pyLogger.info("解析成功: {}", downloadUrl);
                    return downloadUrl;
                } else {
                    pyLogger.error("parse方法返回值类型错误，期望String，实际: {}", 
                            result.getMetaObject().toString());
                    throw new RuntimeException("parse方法返回值类型错误");
                }
            } catch (Exception e) {
                pyLogger.error("Python解析器执行失败: {}", e.getMessage());
                throw new RuntimeException("Python解析器执行失败: " + e.getMessage(), e);
            }
        });
    }
    
    @Override
    public Future<List<FileInfo>> parseFileList() {
        pyLogger.info("开始执行Python文件列表解析: {}", config.getType());
        
        return EXECUTOR.executeBlocking(() -> {
            // 使用池化的 Context，自动归还
            try (PyContextPool.PooledContext pc = CONTEXT_POOL.acquire()) {
                Context context = pc.getContext();
                // 注入Java对象到Python环境
                Value bindings = context.getBindings("python");
                bindings.putMember("http", httpClient);
                bindings.putMember("logger", pyLogger);
                bindings.putMember("share_link_info", shareLinkInfoWrapper);
                bindings.putMember("crypto", cryptoUtils);
                
                // 执行Python代码（已支持真正的 pip 包）
                context.eval("python", config.getPyCode());
                
                // 调用parseFileList函数
                Value parseFileListFunc = bindings.getMember("parse_file_list");
                if (parseFileListFunc == null || !parseFileListFunc.canExecute()) {
                    throw new RuntimeException("Python代码中未找到parse_file_list函数");
                }
                
                Value result = parseFileListFunc.execute(shareLinkInfoWrapper, httpClient, pyLogger);
                
                List<FileInfo> fileList = convertToFileInfoList(result);
                pyLogger.info("文件列表解析成功，共 {} 个文件", fileList.size());
                return fileList;
            } catch (Exception e) {
                pyLogger.error("Python文件列表解析失败: {}", e.getMessage());
                throw new RuntimeException("Python文件列表解析失败: " + e.getMessage(), e);
            }
        });
    }
    
    @Override
    public Future<String> parseById() {
        pyLogger.info("开始执行Python按ID解析: {}", config.getType());
        
        return EXECUTOR.executeBlocking(() -> {
            // 使用池化的 Context，自动归还
            try (PyContextPool.PooledContext pc = CONTEXT_POOL.acquire()) {
                Context context = pc.getContext();
                // 注入Java对象到Python环境
                Value bindings = context.getBindings("python");
                bindings.putMember("http", httpClient);
                bindings.putMember("logger", pyLogger);
                bindings.putMember("share_link_info", shareLinkInfoWrapper);
                bindings.putMember("crypto", cryptoUtils);
                
                // 执行Python代码（已支持真正的 pip 包）
                context.eval("python", config.getPyCode());
                
                // 调用parseById函数
                Value parseByIdFunc = bindings.getMember("parse_by_id");
                if (parseByIdFunc == null || !parseByIdFunc.canExecute()) {
                    throw new RuntimeException("Python代码中未找到parse_by_id函数");
                }
                
                Value result = parseByIdFunc.execute(shareLinkInfoWrapper, httpClient, pyLogger);
                
                if (result.isString()) {
                    String downloadUrl = result.asString();
                    pyLogger.info("按ID解析成功: {}", downloadUrl);
                    return downloadUrl;
                } else {
                    pyLogger.error("parse_by_id方法返回值类型错误，期望String，实际: {}", 
                            result.getMetaObject().toString());
                    throw new RuntimeException("parse_by_id方法返回值类型错误");
                }
            } catch (Exception e) {
                pyLogger.error("Python按ID解析失败: {}", e.getMessage());
                throw new RuntimeException("Python按ID解析失败: " + e.getMessage(), e);
            }
        });
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
            if (item.hasMember("size_str") || item.hasMember("sizeStr")) {
                Value val = item.hasMember("size_str") ? item.getMember("size_str") : item.getMember("sizeStr");
                if (val != null && !val.isNull()) {
                    fileInfo.setSizeStr(val.asString());
                }
            }
            if (item.hasMember("create_time") || item.hasMember("createTime")) {
                Value val = item.hasMember("create_time") ? item.getMember("create_time") : item.getMember("createTime");
                if (val != null && !val.isNull()) {
                    fileInfo.setCreateTime(val.asString());
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
            pyLogger.error("转换FileInfo对象失败", e);
            return null;
        }
    }
}
