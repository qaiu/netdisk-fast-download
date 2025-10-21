package cn.qaiu.parser;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.WebClientVertxInit;
import io.vertx.core.Vertx;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaScript解析器测试
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/17
 */
public class JsParserTest {

    @Test
    public void testJsParserRegistration() {
        // 清理注册表
        CustomParserRegistry.clear();
        
        // 初始化Vertx
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        // 检查是否加载了JavaScript解析器
        CustomParserConfig config = CustomParserRegistry.get("demo_js");
        assert config != null : "JavaScript解析器未加载";
        assert config.isJsParser() : "解析器类型错误";
        assert "演示网盘(JS)".equals(config.getDisplayName()) : "显示名称错误";
        
        System.out.println("✓ JavaScript解析器注册测试通过");
    }
    
    @Test
    public void testJsParserExecution() {
        // 清理注册表
        CustomParserRegistry.clear();
        
        // 初始化Vertx
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        try {
            // 创建解析器
            IPanTool tool = ParserCreate.fromType("demo_js")
                    .shareKey("1")
                    .setShareLinkInfoPwd("test")
                    .createTool();
            
            // 测试parse方法
            String downloadUrl = tool.parseSync();
            assert downloadUrl != null && downloadUrl.contains("cdn.example.com") : 
                    "parse方法返回结果错误: " + downloadUrl;
            
            System.out.println("✓ JavaScript解析器执行测试通过");
            System.out.println("  下载链接: " + downloadUrl);
            
        } catch (Exception e) {
            System.err.println("✗ JavaScript解析器执行测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    @Test
    public void testJsParserFileList() {
        // 清理注册表
        CustomParserRegistry.clear();
        
        // 初始化Vertx
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        try {
            // 创建解析器
            IPanTool tool = ParserCreate.fromType("demo_js")
                    .shareKey("1")
                    .setShareLinkInfoPwd("test")
                    .createTool();
            
            // 测试parseFileList方法
            List<FileInfo> fileList = tool.parseFileList().toCompletionStage().toCompletableFuture().join();
            assert fileList != null : "parseFileList方法返回结果错误";
            
            System.out.println("✓ JavaScript文件列表解析测试通过");
            System.out.println("  文件数量: " + fileList.size());
            
            // 如果有文件，检查第一个文件
            if (!fileList.isEmpty()) {
                FileInfo firstFile = fileList.get(0);
                assert firstFile.getFileName() != null : "文件名不能为空";
                assert firstFile.getFileId() != null : "文件ID不能为空";
                System.out.println("  第一个文件: " + firstFile.getFileName());
            } else {
                System.out.println("  文件列表为空（这是正常的，因为使用的是测试API）");
            }
            
        } catch (Exception e) {
            System.err.println("✗ JavaScript文件列表解析测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    @Test
    public void testJsParserById() {
        // 清理注册表
        CustomParserRegistry.clear();
        
        // 初始化Vertx
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        try {
            // 创建ShareLinkInfo
            Map<String, Object> otherParam = new HashMap<>();
            Map<String, Object> paramJson = new HashMap<>();
            paramJson.put("fileId", "1");
            paramJson.put("id", "1");
            otherParam.put("paramJson", paramJson);
            
            ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                    .type("demo_js")
                    .panName("演示网盘(JS)")
                    .shareKey("1")
                    .sharePassword("test")
                    .otherParam(otherParam)
                    .build();
            
            // 创建解析器
            IPanTool tool = ParserCreate.fromType("demo_js")
                    .shareKey("1")
                    .setShareLinkInfoPwd("test")
                    .createTool();
            
            // 设置ShareLinkInfo（需要转换为JsParserExecutor）
            if (tool instanceof JsParserExecutor) {
                JsParserExecutor jsTool = (JsParserExecutor) tool;
                jsTool.getShareLinkInfo().setOtherParam(otherParam);
            }
            
            // 测试parseById方法
            String downloadUrl = tool.parseById().toCompletionStage().toCompletableFuture().join();
            assert downloadUrl != null && downloadUrl.contains("cdn.example.com") : 
                    "parseById方法返回结果错误: " + downloadUrl;
            
            System.out.println("✓ JavaScript按ID解析测试通过");
            System.out.println("  下载链接: " + downloadUrl);
            
        } catch (Exception e) {
            System.err.println("✗ JavaScript按ID解析测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
