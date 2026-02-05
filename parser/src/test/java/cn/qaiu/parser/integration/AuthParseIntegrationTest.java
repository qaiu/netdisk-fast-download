package cn.qaiu.parser.integration;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.ParserCreate;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.HeadersMultiMap;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 带认证的解析集成测试
 * 
 * 使用方式：
 * 1. 在 src/test/resources/auth-test.properties 中配置认证信息
 * 2. 运行测试
 * 
 * 配置文件格式：
 * qk.cookie=__pus=xxx; __kp=xxx; ...
 * qk.url=https://pan.quark.cn/s/xxx
 * uc.cookie=__pus=xxx; __kp=xxx; ...
 * uc.url=https://fast.uc.cn/s/xxx
 * fj.cookie=your_cookie_here
 * fj.url=https://share.feijipan.com/s/xxx
 * fj.pwd=1234
 */
public class AuthParseIntegrationTest {
    
    private static final String CONFIG_FILE = "src/test/resources/auth-test.properties";
    private static Properties config;
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   带认证的解析集成测试");
        System.out.println("========================================\n");
        
        // 加载配置
        if (!loadConfig()) {
            System.err.println("❌ 无法加载配置文件: " + CONFIG_FILE);
            System.out.println("\n请创建配置文件并添加认证信息：");
            printConfigExample();
            return;
        }
        
        System.out.println("✓ 配置文件加载成功\n");
        
        // 测试夸克网盘
        if (hasConfig("qk")) {
            testQuark();
        } else {
            System.out.println("⏭ 跳过夸克网盘测试（未配置）\n");
        }
        
        // 测试 UC 网盘
        if (hasConfig("uc")) {
            testUc();
        } else {
            System.out.println("⏭ 跳过 UC 网盘测试（未配置）\n");
        }
        
        // 测试小飞机网盘
        if (hasConfig("fj")) {
            testFeiji();
        } else {
            System.out.println("⏭ 跳过小飞机网盘测试（未配置）\n");
        }
        
        System.out.println("========================================");
        System.out.println("   集成测试完成");
        System.out.println("========================================");
        
        // 给异步操作一些时间完成
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.exit(0);
    }
    
    private static boolean loadConfig() {
        config = new Properties();
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            config.load(reader);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    private static boolean hasConfig(String prefix) {
        return config.containsKey(prefix + ".url");
    }
    
    private static String getConfig(String key) {
        return config.getProperty(key, "");
    }
    
    private static void testQuark() {
        System.out.println("=== 测试夸克网盘解析（带认证）===");
        
        String url = getConfig("qk.url");
        String cookie = getConfig("qk.cookie");
        String pwd = getConfig("qk.pwd");
        
        System.out.println("分享链接: " + url);
        System.out.println("Cookie: " + maskCookie(cookie));
        if (!pwd.isEmpty()) {
            System.out.println("密码: " + pwd);
        }
        
        try {
            // 创建认证配置
            MultiMap auths = new HeadersMultiMap();
            auths.set("cookie", cookie);
            
            Map<String, Object> otherParam = new HashMap<>();
            otherParam.put("auths", auths);
            
            // 创建解析器
            ParserCreate parserCreate = ParserCreate.fromShareUrl(url);
            ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
            if (!pwd.isEmpty()) {
                shareLinkInfo.setSharePassword(pwd);
            }
            shareLinkInfo.setOtherParam(otherParam);
            
            IPanTool tool = parserCreate.createTool();
            
            System.out.println("\n开始解析...");
            
            // 异步解析
            CountDownLatch latch = new CountDownLatch(1);
            final long startTime = System.currentTimeMillis();
            
            tool.parse().onSuccess(result -> {
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("\n✅ 夸克网盘解析成功!");
                System.out.println("耗时: " + duration + "ms");
                System.out.println("直链: " + result);
                
                // 验证直链格式
                if (result != null && result.startsWith("http")) {
                    System.out.println("✓ 直链格式正确");
                } else {
                    System.out.println("⚠️ 直链格式异常");
                }
                latch.countDown();
            }).onFailure(error -> {
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("\n❌ 夸克网盘解析失败!");
                System.out.println("耗时: " + duration + "ms");
                System.out.println("错误: " + error.getMessage());
                if (error.getCause() != null) {
                    System.out.println("原因: " + error.getCause().getMessage());
                }
                latch.countDown();
            });
            
            // 等待结果（最多30秒）
            if (!latch.await(30, TimeUnit.SECONDS)) {
                System.out.println("\n⏱️ 解析超时（30秒）");
            }
            
        } catch (Exception e) {
            System.out.println("\n❌ 测试异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println();
    }
    
    private static void testUc() {
        System.out.println("=== 测试 UC 网盘解析（带认证）===");
        
        String url = getConfig("uc.url");
        String cookie = getConfig("uc.cookie");
        String pwd = getConfig("uc.pwd");
        
        System.out.println("分享链接: " + url);
        System.out.println("Cookie: " + maskCookie(cookie));
        if (!pwd.isEmpty()) {
            System.out.println("密码: " + pwd);
        }
        
        try {
            // 创建认证配置
            MultiMap auths = new HeadersMultiMap();
            auths.set("cookie", cookie);
            
            Map<String, Object> otherParam = new HashMap<>();
            otherParam.put("auths", auths);
            
            // 创建解析器
            ParserCreate parserCreate = ParserCreate.fromShareUrl(url);
            ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
            if (!pwd.isEmpty()) {
                shareLinkInfo.setSharePassword(pwd);
            }
            shareLinkInfo.setOtherParam(otherParam);
            
            IPanTool tool = parserCreate.createTool();
            
            System.out.println("\n开始解析...");
            
            // 异步解析
            CountDownLatch latch = new CountDownLatch(1);
            final long startTime = System.currentTimeMillis();
            
            tool.parse().onSuccess(result -> {
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("\n✅ UC 网盘解析成功!");
                System.out.println("耗时: " + duration + "ms");
                System.out.println("直链: " + result);
                
                // 验证直链格式
                if (result != null && result.startsWith("http")) {
                    System.out.println("✓ 直链格式正确");
                } else {
                    System.out.println("⚠️ 直链格式异常");
                }
                latch.countDown();
            }).onFailure(error -> {
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("\n❌ UC 网盘解析失败!");
                System.out.println("耗时: " + duration + "ms");
                System.out.println("错误: " + error.getMessage());
                if (error.getCause() != null) {
                    System.out.println("原因: " + error.getCause().getMessage());
                }
                latch.countDown();
            });
            
            // 等待结果（最多30秒）
            if (!latch.await(30, TimeUnit.SECONDS)) {
                System.out.println("\n⏱️ 解析超时（30秒）");
            }
            
        } catch (Exception e) {
            System.out.println("\n❌ 测试异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println();
    }
    
    private static void testFeiji() {
        System.out.println("=== 测试小飞机网盘解析（带认证）===");
        
        String url = getConfig("fj.url");
        String username = getConfig("fj.username");
        String password = getConfig("fj.password");
        String pwd = getConfig("fj.pwd");
        
        System.out.println("分享链接: " + url);
        System.out.println("用户名: " + (username.isEmpty() ? "无" : username));
        System.out.println("密码: " + (password.isEmpty() ? "无" : "******"));
        if (!pwd.isEmpty()) {
            System.out.println("提取码: " + pwd);
        }
        
        try {
            // 创建认证配置
            MultiMap auths = new HeadersMultiMap();
            if (!username.isEmpty() && !password.isEmpty()) {
                auths.set("username", username);
                auths.set("password", password);
            }
            
            Map<String, Object> otherParam = new HashMap<>();
            if (!username.isEmpty()) {
                otherParam.put("auths", auths);
            }
            
            // 创建解析器
            ParserCreate parserCreate = ParserCreate.fromShareUrl(url);
            ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
            if (!pwd.isEmpty()) {
                shareLinkInfo.setSharePassword(pwd);
            }
            // 设置认证参数
            if (!username.isEmpty()) {
                shareLinkInfo.setOtherParam(otherParam);
            }
            
            IPanTool tool = parserCreate.createTool();
            
            System.out.println("\n开始解析...");
            
            // 异步解析
            CountDownLatch latch = new CountDownLatch(1);
            final long startTime = System.currentTimeMillis();
            
            tool.parse().onSuccess(result -> {
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("\n✅ 小飞机网盘解析成功!");
                System.out.println("耗时: " + duration + "ms");
                System.out.println("直链: " + result);
                
                // 验证直链格式
                if (result != null && result.startsWith("http")) {
                    System.out.println("✓ 直链格式正确");
                } else {
                    System.out.println("⚠️ 直链格式异常");
                }
                latch.countDown();
            }).onFailure(error -> {
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("\n❌ 小飞机网盘解析失败!");
                System.out.println("耗时: " + duration + "ms");
                System.out.println("错误: " + error.getMessage());
                if (error.getCause() != null) {
                    System.out.println("原因: " + error.getCause().getMessage());
                }
                latch.countDown();
            });
            
            // 等待结果（最多30秒）
            if (!latch.await(30, TimeUnit.SECONDS)) {
                System.out.println("\n⏱️ 解析超时（30秒）");
            }
            
        } catch (Exception e) {
            System.out.println("\n❌ 测试异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println();
    }
    
    private static String maskCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) {
            return "(未配置)";
        }
        if (cookie.length() <= 20) {
            return cookie.substring(0, Math.min(10, cookie.length())) + "...";
        }
        return cookie.substring(0, 10) + "..." + cookie.substring(cookie.length() - 10);
    }
    
    private static void printConfigExample() {
        System.out.println("\n配置文件示例 (" + CONFIG_FILE + "):");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("# 夸克网盘配置");
        System.out.println("qk.cookie=__pus=xxx; __kp=xxx; __kps=xxx; __ktd=xxx; __uid=xxx; __puus=xxx");
        System.out.println("qk.url=https://pan.quark.cn/s/xxxxxxxxxx");
        System.out.println("qk.pwd=");
        System.out.println();
        System.out.println("# UC 网盘配置");
        System.out.println("uc.cookie=__pus=xxx; __kp=xxx; __kps=xxx; __ktd=xxx; __uid=xxx; __puus=xxx");
        System.out.println("uc.url=https://fast.uc.cn/s/xxxxxxxxxx");
        System.out.println("uc.pwd=");
        System.out.println();
        System.out.println("# 小飞机网盘配置（大文件需要认证）");
        System.out.println("fj.cookie=your_session_cookie_here");
        System.out.println("fj.url=https://share.feijipan.com/s/xxxxxxxxxx");
        System.out.println("fj.pwd=1234");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
