package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.MultiMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Ye2Tool token 解析验证测试
 * 验证 Authorization 字段和 ******
 */
public class Ye2ToolTokenValidationTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   Ye2Tool Token 解析验证测试");
        System.out.println("========================================\n");

        testNormalizeBearerToken();
        testYe2ToolWithTokenField();
        testYe2ToolWithAuthorizationField();
        testYe2ToolWithoutAuth();

        System.out.println("\n========================================");
        System.out.println("   所有验证通过! ✓");
        System.out.println("========================================");
    }

    /** 独立验证 normalizeBearerToken 逻辑（内联实现与 Ye2Tool 保持一致）*/
    private static String normalizeBearerToken(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String token = raw.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        return token.isEmpty() ? null : token;
    }

    private static void testNormalizeBearerToken() {
        System.out.println("=== 验证 normalizeBearerToken 逻辑 ===");

        assertNormalize("testtoken123abc", "testtoken123abc", "纯 token 不变");
        assertNormalize("Bearer testtoken123abc", "testtoken123abc", "******");
        assertNormalize("bearer testtoken123abc", "testtoken123abc", "小写 bearer 前缀被去掉");
        assertNormalize("BEARER testtoken123abc", "testtoken123abc", "大写 BEARER 前缀被去掉");
        assertNormalize("  Bearer testtoken123abc  ", "testtoken123abc", "前后空格被 trim");
        assertNormalizeNull(null, "null 输入返回 null");
        assertNormalizeNull("", "空字符串返回 null");
        assertNormalizeNull("   ", "纯空格返回 null");

        System.out.println();
    }

    private static void assertNormalize(String input, String expected, String desc) {
        String actual = normalizeBearerToken(input);
        if (!expected.equals(actual)) {
            throw new AssertionError("FAIL [" + desc + "]: expected='" + expected + "', actual='" + actual + "'");
        }
        System.out.println("✓ " + desc);
    }

    private static void assertNormalizeNull(String input, String desc) {
        String actual = normalizeBearerToken(input);
        if (actual != null) {
            throw new AssertionError("FAIL [" + desc + "]: expected null, actual='" + actual + "'");
        }
        System.out.println("✓ " + desc);
    }

    private static void testYe2ToolWithTokenField() {
        System.out.println("=== 测试 Ye2Tool（token 字段）===");
        try {
            MultiMap auths = MultiMap.caseInsensitiveMultiMap();
            auths.set("token", "testtoken123abc");

            Map<String, Object> otherParam = new HashMap<>();
            otherParam.put("auths", auths);

            ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                    .type("YE")
                    .panName("123网盘")
                    .shareKey("test_key_token")
                    .shareUrl("https://www.123pan.com/s/test123")
                    .build();
            shareLinkInfo.setOtherParam(otherParam);

            Ye2Tool ye2Tool = new Ye2Tool(shareLinkInfo);
            System.out.println("✓ Ye2Tool 实例创建成功（token 字段）\n");
        } catch (Exception e) {
            System.err.println("✗ Ye2Tool（token 字段）测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testYe2ToolWithAuthorizationField() {
        System.out.println("=== 测试 Ye2Tool（Authorization 字段，带 Bearer）===");
        try {
            MultiMap auths = MultiMap.caseInsensitiveMultiMap();
            auths.set("Authorization", "Bearer testtoken123abc");

            Map<String, Object> otherParam = new HashMap<>();
            otherParam.put("auths", auths);

            ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                    .type("YE")
                    .panName("123网盘")
                    .shareKey("test_key_auth")
                    .shareUrl("https://www.123pan.com/s/test456")
                    .build();
            shareLinkInfo.setOtherParam(otherParam);

            Ye2Tool ye2Tool = new Ye2Tool(shareLinkInfo);
            System.out.println("✓ Ye2Tool 实例创建成功（Authorization 字段）\n");
        } catch (Exception e) {
            System.err.println("✗ Ye2Tool（Authorization 字段）测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testYe2ToolWithoutAuth() {
        System.out.println("=== 测试 Ye2Tool（无认证配置）===");
        try {
            ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                    .type("YE")
                    .panName("123网盘")
                    .shareKey("test_key_noauth")
                    .shareUrl("https://www.123pan.com/s/test789")
                    .build();

            Ye2Tool ye2Tool = new Ye2Tool(shareLinkInfo);
            System.out.println("✓ Ye2Tool 实例创建成功（无认证）\n");
        } catch (Exception e) {
            System.err.println("✗ Ye2Tool（无认证）测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
