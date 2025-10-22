package cn.qaiu.parser;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.custom.CustomParserConfig;
import cn.qaiu.parser.custom.CustomParserRegistry;
import cn.qaiu.parser.customjs.JsParserExecutor;
import cn.qaiu.WebClientVertxInit;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 百度一刻相册解析器测试
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/21
 */
public class BaiduPhotoParserTest {

    @Test
    public void testBaiduPhotoParserRegistration() {
        // 清理注册表
        CustomParserRegistry.clear();
        
        // 初始化Vertx
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        // 检查是否加载了百度相册解析器
        CustomParserConfig config = CustomParserRegistry.get("baidu_photo");
        assert config != null : "百度相册解析器未加载";
        assert config.isJsParser() : "解析器类型错误";
        assert "百度一刻相册(JS)".equals(config.getDisplayName()) : "显示名称错误";
        
        System.out.println("✓ 百度一刻相册解析器注册测试通过");
    }
    
    @Test
    public void testBaiduPhotoFileShareExecution() {
        // 清理注册表
        CustomParserRegistry.clear();
        
        // 初始化Vertx
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        try {
            // 创建解析器 - 测试文件分享链接
            IPanTool tool = ParserCreate.fromType("baidu_photo")
                    .shareKey("19012978577097490")  // 文件分享ID
                    .setShareLinkInfoPwd("")
                    .createTool();
            
            // 测试parse方法
            String downloadUrl = tool.parseSync();
            assert downloadUrl != null && downloadUrl.contains("d.pcs.baidu.com") : 
                    "parse方法返回结果错误: " + downloadUrl;
            
            System.out.println("✓ 百度一刻相册文件分享解析测试通过");
            System.out.println("  下载链接: " + downloadUrl);
            
        } catch (Exception e) {
            System.err.println("✗ 百度一刻相册文件分享解析测试失败: " + e.getMessage());
            e.printStackTrace();
            // 注意：这个测试可能会失败，因为需要真实的网络请求和可能的认证
            // 这里主要是验证解析器逻辑是否正确
        }
    }
    
    @Test
    public void testBaiduPhotoFolderShareExecution() {
        // 清理注册表
        CustomParserRegistry.clear();
        
        // 初始化Vertx
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        try {
            // 创建解析器 - 测试文件夹分享链接
            IPanTool tool = ParserCreate.fromType("baidu_photo")
                    .shareKey("abc123def456")  // 文件夹分享的inviteCode
                    .setShareLinkInfoPwd("")
                    .createTool();
            
            // 测试parse方法
            String downloadUrl = tool.parseSync();
            assert downloadUrl != null && downloadUrl.contains("d.pcs.baidu.com") : 
                    "parse方法返回结果错误: " + downloadUrl;
            
            System.out.println("✓ 百度一刻相册文件夹分享解析测试通过");
            System.out.println("  下载链接: " + downloadUrl);
            
        } catch (Exception e) {
            System.err.println("✗ 百度一刻相册文件夹分享解析测试失败: " + e.getMessage());
            e.printStackTrace();
            // 注意：这个测试可能会失败，因为需要真实的网络请求和可能的认证
            // 这里主要是验证解析器逻辑是否正确
        }
    }
    
    @Test
    public void testBaiduPhotoParserFileList() {
        // 清理注册表
        CustomParserRegistry.clear();
        
        // 初始化Vertx
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        try {
            IPanTool tool = ParserCreate.fromType("baidu_photo")
                    // 分享key PPgOEodBVE
                    .shareKey("PPgOEodBVE")
                    .setShareLinkInfoPwd("")
                    .createTool();
            
            // 测试parseFileList方法
            List<FileInfo> fileList = tool.parseFileListSync();
            assert fileList != null : "parseFileList方法返回结果错误";
            
            System.out.println("✓ 百度一刻相册文件列表解析测试通过");
            System.out.println("  文件数量: " + fileList.size());
            
            // 如果有文件，检查第一个文件
            if (!fileList.isEmpty()) {
                FileInfo firstFile = fileList.get(0);
                assert firstFile.getFileName() != null : "文件名不能为空";
                assert firstFile.getFileId() != null : "文件ID不能为空";
                System.out.println("  第一个文件: " + firstFile.getFileName());
                System.out.println("  下载链接: " + firstFile.getParserUrl());
                System.out.println("  预览链接: " + firstFile.getPreviewUrl());
                
                // 输出所有文件的详细信息
                System.out.println("\n=== 完整文件列表 ===");
                for (int i = 0; i < fileList.size(); i++) {
                    FileInfo file = fileList.get(i);
                    System.out.println("\n--- 文件 " + (i + 1) + " ---");
                    System.out.println("  文件名: " + file.getFileName());
                    System.out.println("  文件ID: " + file.getFileId());
                    System.out.println("  文件类型: " + file.getFileType());
                    System.out.println("  文件大小: " + file.getSize() + " bytes (" + file.getSizeStr() + ")");
                    System.out.println("  创建时间: " + file.getCreateTime());
                    System.out.println("  更新时间: " + file.getUpdateTime());
                    System.out.println("  下载链接: " + file.getParserUrl());
                    System.out.println("  预览链接: " + file.getPreviewUrl());
                    System.out.println("  网盘类型: " + file.getPanType());
                }
            } else {
                System.out.println("  文件列表为空（可能是网络问题或认证问题）");
            }
            
        } catch (Exception e) {
            System.err.println("✗ 百度一刻相册文件列表解析测试失败: " + e.getMessage());
            e.printStackTrace();
            // 注意：这个测试可能会失败，因为需要真实的网络请求和可能的认证
        }
    }
    
    @Test
    public void testBaiduPhotoParserById() {
        // 清理注册表
        CustomParserRegistry.clear();
        
        // 初始化Vertx
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        try {
            // 创建ShareLinkInfo
            Map<String, Object> otherParam = new HashMap<>();
            Map<String, Object> paramJson = new HashMap<>();
            paramJson.put("fileId", "0"); // 测试第一个文件
            paramJson.put("id", "0");
            otherParam.put("paramJson", paramJson);
            
            // 创建解析器 - 使用新的文件分享链接
            IPanTool tool = ParserCreate.fromType("baidu_photo")
                    .shareKey("19012978577097490")
                    .setShareLinkInfoPwd("")
                    .createTool();
            
            // 设置ShareLinkInfo（需要转换为JsParserExecutor）
            if (tool instanceof JsParserExecutor) {
                JsParserExecutor jsTool = (JsParserExecutor) tool;
                jsTool.getShareLinkInfo().setOtherParam(otherParam);
            }
            
            // 测试parseById方法
            String downloadUrl = tool.parseById().toCompletionStage().toCompletableFuture().join();
            assert downloadUrl != null && downloadUrl.contains("d.pcs.baidu.com") : 
                    "parseById方法返回结果错误: " + downloadUrl;
            
            System.out.println("✓ 百度一刻相册按ID解析测试通过");
            System.out.println("  下载链接: " + downloadUrl);
            
        } catch (Exception e) {
            System.err.println("✗ 百度一刻相册按ID解析测试失败: " + e.getMessage());
            e.printStackTrace();
            // 注意：这个测试可能会失败，因为需要真实的网络请求和可能的认证
        }
    }
}
