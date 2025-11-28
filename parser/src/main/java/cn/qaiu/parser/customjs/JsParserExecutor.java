package cn.qaiu.parser.customjs;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.custom.CustomParserConfig;
import io.vertx.core.Future;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonObject;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaScript解析器执行器
 * 实现IPanTool接口，执行JavaScript解析器逻辑
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/17
 */
public class JsParserExecutor implements IPanTool {
    
    private static final Logger log = LoggerFactory.getLogger(JsParserExecutor.class);
    
    private static final WorkerExecutor EXECUTOR = WebClientVertxInit.get().createSharedWorkerExecutor("parser-executor", 32);
    
    private final CustomParserConfig config;
    private final ShareLinkInfo shareLinkInfo;
    private final ScriptEngine engine;
    private final JsHttpClient httpClient;
    private final JsLogger jsLogger;
    private final JsShareLinkInfoWrapper shareLinkInfoWrapper;
    
    public JsParserExecutor(ShareLinkInfo shareLinkInfo, CustomParserConfig config) {
        this.config = config;
        this.shareLinkInfo = shareLinkInfo;
        this.engine = initEngine();
        
        // 检查是否有代理配置
        JsonObject proxyConfig = null;
        if (shareLinkInfo.getOtherParam().containsKey("proxy")) {
            proxyConfig = (JsonObject) shareLinkInfo.getOtherParam().get("proxy");
        }
        
        this.httpClient = new JsHttpClient(proxyConfig);
        this.jsLogger = new JsLogger("JsParser-" + config.getType());
        this.shareLinkInfoWrapper = new JsShareLinkInfoWrapper(shareLinkInfo);
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
            
            // 注入Java对象到JavaScript环境
            engine.put("http", httpClient);
            engine.put("logger", jsLogger);
            engine.put("shareLinkInfo", shareLinkInfoWrapper);
            
            // 禁用Java对象访问
            engine.eval("var Java = undefined;");
            engine.eval("var JavaImporter = undefined;");
            engine.eval("var Packages = undefined;");
            engine.eval("var javax = undefined;");
            engine.eval("var org = undefined;");
            engine.eval("var com = undefined;");
            
            log.debug("🔒 安全的JavaScript引擎初始化成功，解析器类型: {}", config.getType());
            
            // 执行JavaScript代码
            engine.eval(config.getJsCode());
            
            return engine;
            
        } catch (Exception e) {
            log.error("JavaScript引擎初始化失败", e);
            throw new RuntimeException("JavaScript引擎初始化失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Future<String> parse() {
        jsLogger.info("开始执行JavaScript解析器: {}", config.getType());
        
        // 使用executeBlocking在工作线程上执行，避免阻塞EventLoop线程
        return EXECUTOR.executeBlocking(() -> {
            // 直接调用全局parse函数
            Object parseFunction = engine.get("parse");
            if (parseFunction == null) {
                throw new RuntimeException("JavaScript代码中未找到parse函数");
            }
            
            if (parseFunction instanceof ScriptObjectMirror parseMirror) {

                Object result = parseMirror.call(null, shareLinkInfoWrapper, httpClient, jsLogger);
                
                if (result instanceof String) {
                    jsLogger.info("解析成功: {}", result);
                    return (String) result;
                } else {
                    jsLogger.error("parse方法返回值类型错误，期望String，实际: {}", 
                            result != null ? result.getClass().getSimpleName() : "null");
                    throw new RuntimeException("parse方法返回值类型错误");
                }
            } else {
                throw new RuntimeException("parse函数类型错误");
            }
        });
    }
    
    @Override
    public Future<List<FileInfo>> parseFileList() {
        jsLogger.info("开始执行JavaScript文件列表解析: {}", config.getType());
        
        // 使用executeBlocking在工作线程上执行，避免阻塞EventLoop线程
        return EXECUTOR.executeBlocking(() -> {
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
    }
    
    @Override
    public Future<String> parseById() {
        jsLogger.info("开始执行JavaScript按ID解析: {}", config.getType());
        
        // 使用executeBlocking在工作线程上执行，避免阻塞EventLoop线程
        return EXECUTOR.executeBlocking(() -> {
            // 直接调用全局parseById函数
            Object parseByIdFunction = engine.get("parseById");
            if (parseByIdFunction == null) {
                throw new RuntimeException("JavaScript代码中未找到parseById函数");
            }
            
            // 调用parseById方法
            if (parseByIdFunction instanceof ScriptObjectMirror parseByIdMirror) {

                Object result = parseByIdMirror.call(null, shareLinkInfoWrapper, httpClient, jsLogger);
                
                if (result instanceof String) {
                    jsLogger.info("按ID解析成功: {}", result);
                    return (String) result;
                } else {
                    jsLogger.error("parseById方法返回值类型错误，期望String，实际: {}", 
                            result != null ? result.getClass().getSimpleName() : "null");
                    throw new RuntimeException("parseById方法返回值类型错误");
                }
            } else {
                throw new RuntimeException("parseById函数类型错误");
            }
        });
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
            jsLogger.error("转换FileInfo对象失败", e);
            return null;
        }
    }
}
