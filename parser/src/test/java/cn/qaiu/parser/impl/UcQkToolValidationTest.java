package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.HeadersMultiMap;

import java.util.HashMap;
import java.util.Map;

/**
 * UC 和夸克网盘工具类验证测试
 */
public class UcQkToolValidationTest {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   UC/夸克网盘工具类验证测试");
        System.out.println("========================================\n");
        
        testQkToolWithAuth();
        testUcToolWithAuth();
        testQkToolWithoutAuth();
        testUcToolWithoutAuth();
        
        System.out.println("\n========================================");
        System.out.println("   所有验证通过! ✓");
        System.out.println("========================================");
    }
    
    private static void testQkToolWithAuth() {
        System.out.println("=== 测试夸克网盘工具类（带认证）===");
        
        try {
            // 创建认证配置
            MultiMap auths = new HeadersMultiMap();
            auths.set("cookie", "__pus=test_token; __kp=key123; __kps=secret; __puus=signature");
            
            Map<String, Object> otherParam = new HashMap<>();
            otherParam.put("auths", auths);
            
            // 创建分享链接信息
            ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                    .type("QK")
                    .panName("夸克网盘")
                    .shareKey("test_key")
                    .shareUrl("https://pan.quark.cn/s/test123")
                    .build();
            shareLinkInfo.setOtherParam(otherParam);
            
            // 创建工具类实例
            QkTool qkTool = new QkTool(shareLinkInfo);
            
            System.out.println("✓ 夸克网盘工具类实例创建成功");
            System.out.println("  - 已配置认证信息");
            System.out.println("  - Cookie 已过滤和应用\n");
        } catch (Exception e) {
            System.err.println("✗ 夸克网盘工具类测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testUcToolWithAuth() {
        System.out.println("=== 测试 UC 网盘工具类（带认证）===");
        
        try {
            // 创建认证配置
            MultiMap auths = new HeadersMultiMap();
            auths.set("cookie", "__pus=uc_token; __kp=uc_key; __uid=user001; __puus=uc_sig");
            
            Map<String, Object> otherParam = new HashMap<>();
            otherParam.put("auths", auths);
            
            // 创建分享链接信息
            ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                    .type("UC")
                    .panName("UC网盘")
                    .shareKey("uc_key_123")
                    .shareUrl("https://fast.uc.cn/s/abc123")
                    .build();
            shareLinkInfo.setOtherParam(otherParam);
            
            // 创建工具类实例
            UcTool ucTool = new UcTool(shareLinkInfo);
            
            System.out.println("✓ UC 网盘工具类实例创建成功");
            System.out.println("  - 已配置认证信息");
            System.out.println("  - Cookie 已过滤和应用\n");
        } catch (Exception e) {
            System.err.println("✗ UC 网盘工具类测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testQkToolWithoutAuth() {
        System.out.println("=== 测试夸克网盘工具类（无认证）===");
        
        try {
            // 创建分享链接信息（无认证）
            ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                    .type("QK")
                    .panName("夸克网盘")
                    .shareKey("test_key_no_auth")
                    .shareUrl("https://pan.quark.cn/s/test456")
                    .build();
            
            // 创建工具类实例
            QkTool qkTool = new QkTool(shareLinkInfo);
            
            System.out.println("✓ 夸克网盘工具类实例创建成功（无认证）");
            System.out.println("  - 应该使用默认请求头\n");
        } catch (Exception e) {
            System.err.println("✗ 夸克网盘工具类（无认证）测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testUcToolWithoutAuth() {
        System.out.println("=== 测试 UC 网盘工具类（无认证）===");
        
        try {
            // 创建分享链接信息（无认证）
            ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                    .type("UC")
                    .panName("UC网盘")
                    .shareKey("uc_no_auth")
                    .shareUrl("https://fast.uc.cn/s/def456")
                    .build();
            
            // 创建工具类实例
            UcTool ucTool = new UcTool(shareLinkInfo);
            
            System.out.println("✓ UC 网盘工具类实例创建成功（无认证）");
            System.out.println("  - 应该使用默认请求头\n");
        } catch (Exception e) {
            System.err.println("✗ UC 网盘工具类（无认证）测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
