package cn.qaiu.parser.auth;

import cn.qaiu.util.CookieUtils;

/**
 * 手动测试 Cookie 工具类
 */
public class CookieUtilsManualTest {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   Cookie 工具类手动测试");
        System.out.println("========================================\n");
        
        testCookieFilter();
        testCookieGetValue();
        testCookieUpdate();
        testCookieContainsKey();
        testEmptyCookieHandling();
        testComplexScenario();
        testAllUcQuarkCookieFields();
        
        System.out.println("\n========================================");
        System.out.println("   所有测试通过! ✓");
        System.out.println("========================================");
    }
    
    private static void testCookieFilter() {
        System.out.println("=== 测试 Cookie 过滤功能 ===");
        String fullCookie = "__pus=abc123; __kp=def456; other_cookie=xyz; __puus=token789; random=test; __uid=user001";
        String filtered = CookieUtils.filterUcQuarkCookie(fullCookie);
        
        System.out.println("原始 Cookie: " + fullCookie);
        System.out.println("过滤后 Cookie: " + filtered);
        
        assert filtered.contains("__pus=abc123") : "应包含 __pus";
        assert filtered.contains("__kp=def456") : "应包含 __kp";
        assert filtered.contains("__puus=token789") : "应包含 __puus";
        assert !filtered.contains("other_cookie") : "不应包含 other_cookie";
        assert !filtered.contains("random") : "不应包含 random";
        
        System.out.println("✓ Cookie 过滤测试通过\n");
    }
    
    private static void testCookieGetValue() {
        System.out.println("=== 测试获取 Cookie 值 ===");
        String cookie = "__pus=value1; __kp=value2; __puus=value3";
        
        String pus = CookieUtils.getCookieValue(cookie, "__pus");
        String kp = CookieUtils.getCookieValue(cookie, "__kp");
        String puus = CookieUtils.getCookieValue(cookie, "__puus");
        String notexist = CookieUtils.getCookieValue(cookie, "notexist");
        
        System.out.println("Cookie: " + cookie);
        System.out.println("__pus = " + pus);
        System.out.println("__kp = " + kp);
        System.out.println("__puus = " + puus);
        System.out.println("notexist = " + notexist);
        
        assert "value1".equals(pus) : "__pus 应为 value1";
        assert "value2".equals(kp) : "__kp 应为 value2";
        assert "value3".equals(puus) : "__puus 应为 value3";
        assert notexist == null : "notexist 应为 null";
        
        System.out.println("✓ 获取 Cookie 值测试通过\n");
    }
    
    private static void testCookieUpdate() {
        System.out.println("=== 测试更新 Cookie ===");
        String cookie = "__pus=old_pus; __kp=value2";
        String updated = CookieUtils.updateCookieValue(cookie, "__puus", "__puus=new_puus_value");
        
        System.out.println("更新前: " + cookie);
        System.out.println("更新后: " + updated);
        
        assert updated.contains("__puus=new_puus_value") : "应包含新的 __puus";
        assert updated.contains("__pus=old_pus") : "应保留 __pus";
        assert updated.contains("__kp=value2") : "应保留 __kp";
        
        // 测试替换已存在的值
        String cookie2 = "__pus=old_value; __kp=value2; __puus=old_puus";
        String updated2 = CookieUtils.updateCookieValue(cookie2, "__puus", "__puus=updated_puus");
        
        System.out.println("替换测试 - 更新前: " + cookie2);
        System.out.println("替换测试 - 更新后: " + updated2);
        
        assert updated2.contains("__puus=updated_puus") : "应包含更新的 __puus";
        assert !updated2.contains("__puus=old_puus") : "不应包含旧的 __puus";
        
        System.out.println("✓ 更新 Cookie 测试通过\n");
    }
    
    private static void testCookieContainsKey() {
        System.out.println("=== 测试检查 Cookie key 存在性 ===");
        String cookie = "__pus=value1; __kp=value2; __uid=user123";
        
        boolean hasPus = CookieUtils.containsKey(cookie, "__pus");
        boolean hasKp = CookieUtils.containsKey(cookie, "__kp");
        boolean hasPuus = CookieUtils.containsKey(cookie, "__puus");
        boolean hasNotexist = CookieUtils.containsKey(cookie, "notexist");
        
        System.out.println("Cookie: " + cookie);
        System.out.println("containsKey(__pus): " + hasPus);
        System.out.println("containsKey(__kp): " + hasKp);
        System.out.println("containsKey(__puus): " + hasPuus);
        System.out.println("containsKey(notexist): " + hasNotexist);
        
        assert hasPus : "__pus 应存在";
        assert hasKp : "__kp 应存在";
        assert !hasPuus : "__puus 不应存在";
        assert !hasNotexist : "notexist 不应存在";
        
        System.out.println("✓ 检查 Cookie key 测试通过\n");
    }
    
    private static void testEmptyCookieHandling() {
        System.out.println("=== 测试空 Cookie 处理 ===");
        String emptyFiltered = CookieUtils.filterUcQuarkCookie("");
        String nullFiltered = CookieUtils.filterUcQuarkCookie(null);
        String emptyValue = CookieUtils.getCookieValue("", "__pus");
        String nullValue = CookieUtils.getCookieValue(null, "__pus");
        
        System.out.println("filterUcQuarkCookie(''): '" + emptyFiltered + "'");
        System.out.println("filterUcQuarkCookie(null): '" + nullFiltered + "'");
        System.out.println("getCookieValue('', '__pus'): " + emptyValue);
        System.out.println("getCookieValue(null, '__pus'): " + nullValue);
        
        assert "".equals(emptyFiltered) : "空字符串应返回空字符串";
        assert "".equals(nullFiltered) : "null 应返回空字符串";
        assert emptyValue == null : "空字符串的值应为 null";
        assert nullValue == null : "null 的值应为 null";
        
        System.out.println("✓ 空 Cookie 处理测试通过\n");
    }
    
    private static void testComplexScenario() {
        System.out.println("=== 测试复杂场景：模拟 UC/夸克 Cookie 处理流程 ===");
        
        // 模拟从浏览器获取的完整 Cookie
        String browserCookie = "session_id=xxx; __pus=main_token_here; other=value; " +
                "__kp=key123; __kps=secret456; __ktd=token789; " +
                "__uid=user001; random_cookie=test; __puus=old_signature";
        
        System.out.println("1. 浏览器原始 Cookie:");
        System.out.println("   " + browserCookie);
        
        // 第一步：过滤出必要的字段
        String filtered = CookieUtils.filterUcQuarkCookie(browserCookie);
        System.out.println("\n2. 过滤后的 Cookie (只保留 UC/夸克必需字段):");
        System.out.println("   " + filtered);
        
        assert filtered.contains("__pus=main_token_here") : "应包含 __pus";
        assert filtered.contains("__kp=key123") : "应包含 __kp";
        assert filtered.contains("__puus=old_signature") : "应包含 __puus";
        assert !filtered.contains("session_id") : "不应包含 session_id";
        assert !filtered.contains("random_cookie") : "不应包含 random_cookie";
        
        // 第二步：模拟刷新 __puus
        String newPuus = "__puus=refreshed_signature_from_server";
        String updated = CookieUtils.updateCookieValue(filtered, "__puus", newPuus);
        System.out.println("\n3. 刷新 __puus 后的 Cookie:");
        System.out.println("   " + updated);
        
        assert updated.contains("__puus=refreshed_signature_from_server") : "应包含新的 __puus";
        assert !updated.contains("__puus=old_signature") : "不应包含旧的 __puus";
        assert updated.contains("__pus=main_token_here") : "应保留 __pus";
        
        // 第三步：验证可以获取单个值
        String pusValue = CookieUtils.getCookieValue(updated, "__pus");
        String puusValue = CookieUtils.getCookieValue(updated, "__puus");
        System.out.println("\n4. 提取单个 Cookie 值:");
        System.out.println("   __pus = " + pusValue);
        System.out.println("   __puus = " + puusValue);
        
        assert "main_token_here".equals(pusValue) : "__pus 应为 main_token_here";
        assert "refreshed_signature_from_server".equals(puusValue) : "__puus 应为 refreshed_signature_from_server";
        
        System.out.println("\n✓ 复杂场景测试通过\n");
    }
    
    private static void testAllUcQuarkCookieFields() {
        System.out.println("=== 测试所有 UC/夸克 Cookie 必需字段 ===");
        
        // 包含所有必需字段的 Cookie
        String fullCookie = "__pus=token1; __kp=token2; __kps=token3; " +
                "__ktd=token4; __uid=token5; __puus=token6; " +
                "extra1=value1; extra2=value2";
        
        String filtered = CookieUtils.filterUcQuarkCookie(fullCookie);
        
        System.out.println("原始 Cookie: " + fullCookie);
        System.out.println("过滤后: " + filtered);
        System.out.println("\n验证必需字段:");
        
        // 验证所有必需字段都被保留
        String[] requiredFields = {"__pus", "__kp", "__kps", "__ktd", "__uid", "__puus"};
        for (String field : requiredFields) {
            boolean contains = CookieUtils.containsKey(filtered, field);
            System.out.println("  - " + field + ": " + (contains ? "✓" : "✗"));
            assert contains : "应包含 " + field;
        }
        
        // 验证额外字段被过滤掉
        assert !filtered.contains("extra1") : "不应包含 extra1";
        assert !filtered.contains("extra2") : "不应包含 extra2";
        
        System.out.println("\n✓ 所有字段测试通过\n");
    }
}
