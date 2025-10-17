package cn.qaiu.parser;

import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 自定义解析器功能测试
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/17
 */
public class CustomParserTest {

    @Before
    public void setUp() {
        // 清空注册表，确保测试独立性
        CustomParserRegistry.clear();
    }

    @After
    public void tearDown() {
        // 测试后清理
        CustomParserRegistry.clear();
    }

    @Test
    public void testRegisterCustomParser() {
        // 创建配置
        CustomParserConfig config = CustomParserConfig.builder()
                .type("testpan")
                .displayName("测试网盘")
                .toolClass(TestPanTool.class)
                .standardUrlTemplate("https://testpan.com/s/{shareKey}")
                .panDomain("https://testpan.com")
                .build();

        // 注册
        CustomParserRegistry.register(config);

        // 验证
        assertTrue(CustomParserRegistry.contains("testpan"));
        assertEquals(1, CustomParserRegistry.size());
        
        CustomParserConfig retrieved = CustomParserRegistry.get("testpan");
        assertNotNull(retrieved);
        assertEquals("testpan", retrieved.getType());
        assertEquals("测试网盘", retrieved.getDisplayName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegisterDuplicateType() {
        CustomParserConfig config1 = CustomParserConfig.builder()
                .type("testpan")
                .displayName("测试网盘1")
                .toolClass(TestPanTool.class)
                .build();

        CustomParserConfig config2 = CustomParserConfig.builder()
                .type("testpan")
                .displayName("测试网盘2")
                .toolClass(TestPanTool.class)
                .build();

        // 第一次注册成功
        CustomParserRegistry.register(config1);

        // 第二次注册应该失败，期望抛出 IllegalArgumentException
        CustomParserRegistry.register(config2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegisterConflictWithBuiltIn() {
        // 尝试注册与内置类型冲突的解析器
        CustomParserConfig config = CustomParserConfig.builder()
                .type("lz")  // 蓝奏云的类型
                .displayName("假蓝奏云")
                .toolClass(TestPanTool.class)
                .build();

        // 应该抛出异常，期望抛出 IllegalArgumentException
        CustomParserRegistry.register(config);
    }

    @Test
    public void testUnregisterParser() {
        CustomParserConfig config = CustomParserConfig.builder()
                .type("testpan")
                .displayName("测试网盘")
                .toolClass(TestPanTool.class)
                .build();

        CustomParserRegistry.register(config);
        assertTrue(CustomParserRegistry.contains("testpan"));

        // 注销
        boolean result = CustomParserRegistry.unregister("testpan");
        assertTrue(result);
        assertFalse(CustomParserRegistry.contains("testpan"));
        assertEquals(0, CustomParserRegistry.size());
    }

    @Test
    public void testCreateToolFromCustomParser() {
        // 注册自定义解析器
        CustomParserConfig config = CustomParserConfig.builder()
                .type("testpan")
                .displayName("测试网盘")
                .toolClass(TestPanTool.class)
                .standardUrlTemplate("https://testpan.com/s/{shareKey}")
                .build();
        CustomParserRegistry.register(config);

        // 通过 fromType 创建
        ParserCreate parser = ParserCreate.fromType("testpan")
                .shareKey("abc123")
                .setShareLinkInfoPwd("1234");

        // 验证是自定义解析器
        assertTrue(parser.isCustomParser());
        assertNotNull(parser.getCustomParserConfig());
        assertNull(parser.getPanDomainTemplate());

        // 创建工具
        IPanTool tool = parser.createTool();
        assertNotNull(tool);
        assertTrue(tool instanceof TestPanTool);

        // 验证解析
        String url = tool.parseSync();
        assertTrue(url.contains("abc123"));
        assertTrue(url.contains("1234"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCustomParserNotSupportFromShareUrl() {
        // 注册自定义解析器（不提供正则表达式）
        CustomParserConfig config = CustomParserConfig.builder()
                .type("testpan")
                .displayName("测试网盘")
                .toolClass(TestPanTool.class)
                .build();
        CustomParserRegistry.register(config);

        // fromShareUrl 不应该识别自定义解析器，期望抛出 IllegalArgumentException
        // 使用一个不会被任何内置解析器匹配的URL（不符合域名格式）
        ParserCreate.fromShareUrl("not-a-valid-url");
    }

    @Test
    public void testCustomParserWithRegexSupportFromShareUrl() {
        // 注册支持正则匹配的自定义解析器
        CustomParserConfig config = CustomParserConfig.builder()
                .type("testpan")
                .displayName("测试网盘")
                .toolClass(TestPanTool.class)
                .standardUrlTemplate("https://testpan.com/s/{shareKey}")
                .matchPattern("https://testpan\\.com/s/(?<KEY>[^?]+)(\\?pwd=(?<PWD>.+))?")
                .build();
        CustomParserRegistry.register(config);

        // 测试 fromShareUrl 识别自定义解析器
        ParserCreate parser = ParserCreate.fromShareUrl("https://testpan.com/s/abc123?pwd=pass456");
        
        // 验证是自定义解析器
        assertTrue(parser.isCustomParser());
        assertEquals("testpan", parser.getShareLinkInfo().getType());
        assertEquals("测试网盘", parser.getShareLinkInfo().getPanName());
        assertEquals("abc123", parser.getShareLinkInfo().getShareKey());
        assertEquals("pass456", parser.getShareLinkInfo().getSharePassword());
        assertEquals("https://testpan.com/s/abc123", parser.getShareLinkInfo().getStandardUrl());
    }

    @Test
    public void testCustomParserSupportsFromShareUrl() {
        // 测试 supportsFromShareUrl 方法
        CustomParserConfig config1 = CustomParserConfig.builder()
                .type("test1")
                .displayName("测试1")
                .toolClass(TestPanTool.class)
                .matchPattern("https://test1\\.com/s/(?<KEY>.+)")
                .build();
        assertTrue(config1.supportsFromShareUrl());

        CustomParserConfig config2 = CustomParserConfig.builder()
                .type("test2")
                .displayName("测试2")
                .toolClass(TestPanTool.class)
                .build();
        assertFalse(config2.supportsFromShareUrl());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCustomParserNotSupportNormalizeShareLink() {
        // 注册不支持正则匹配的自定义解析器
        CustomParserConfig config = CustomParserConfig.builder()
                .type("testpan")
                .displayName("测试网盘")
                .toolClass(TestPanTool.class)
                .build();
        CustomParserRegistry.register(config);

        ParserCreate parser = ParserCreate.fromType("testpan");

        // 不支持正则匹配的自定义解析器不支持 normalizeShareLink，期望抛出 UnsupportedOperationException
        parser.normalizeShareLink();
    }

    @Test
    public void testCustomParserWithRegexSupportNormalizeShareLink() {
        // 注册支持正则匹配的自定义解析器
        CustomParserConfig config = CustomParserConfig.builder()
                .type("testpan")
                .displayName("测试网盘")
                .toolClass(TestPanTool.class)
                .standardUrlTemplate("https://testpan.com/s/{shareKey}")
                .matchPattern("https://testpan\\.com/s/(?<KEY>[^?]+)(\\?pwd=(?<PWD>.+))?")
                .build();
        CustomParserRegistry.register(config);

        // 通过 fromType 创建，然后设置分享URL
        ParserCreate parser = ParserCreate.fromType("testpan")
                .shareKey("abc123")
                .setShareLinkInfoPwd("pass456");
        
        // 设置分享URL
        parser.getShareLinkInfo().setShareUrl("https://testpan.com/s/abc123?pwd=pass456");
        
        // 支持正则匹配的自定义解析器支持 normalizeShareLink
        ParserCreate result = parser.normalizeShareLink();
        
        // 验证结果
        assertTrue(result.isCustomParser());
        assertEquals("abc123", result.getShareLinkInfo().getShareKey());
        assertEquals("pass456", result.getShareLinkInfo().getSharePassword());
        assertEquals("https://testpan.com/s/abc123", result.getShareLinkInfo().getStandardUrl());
    }

    @Test
    public void testGenPathSuffix() {
        CustomParserConfig config = CustomParserConfig.builder()
                .type("testpan")
                .displayName("测试网盘")
                .toolClass(TestPanTool.class)
                .standardUrlTemplate("https://testpan.com/s/{shareKey}")  // 添加URL模板
                .build();
        CustomParserRegistry.register(config);

        ParserCreate parser = ParserCreate.fromType("testpan")
                .shareKey("abc123")
                .setShareLinkInfoPwd("pass123");

        String pathSuffix = parser.genPathSuffix();
        assertEquals("testpan/abc123@pass123", pathSuffix);
    }

    @Test
    public void testGetAll() {
        CustomParserConfig config1 = CustomParserConfig.builder()
                .type("testpan1")
                .displayName("测试网盘1")
                .toolClass(TestPanTool.class)
                .build();

        CustomParserConfig config2 = CustomParserConfig.builder()
                .type("testpan2")
                .displayName("测试网盘2")
                .toolClass(TestPanTool.class)
                .build();

        CustomParserRegistry.register(config1);
        CustomParserRegistry.register(config2);

        var allParsers = CustomParserRegistry.getAll();
        assertEquals(2, allParsers.size());
        assertTrue(allParsers.containsKey("testpan1"));
        assertTrue(allParsers.containsKey("testpan2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfigBuilderValidationMissingType() {
        // 测试缺少 type，期望抛出 IllegalArgumentException
        CustomParserConfig.builder()
                .displayName("测试")
                .toolClass(TestPanTool.class)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfigBuilderValidationMissingDisplayName() {
        // 测试缺少 displayName，期望抛出 IllegalArgumentException
        CustomParserConfig.builder()
                .type("test")
                .toolClass(TestPanTool.class)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfigBuilderValidationMissingToolClass() {
        // 测试缺少 toolClass，期望抛出 IllegalArgumentException
        CustomParserConfig.builder()
                .type("test")
                .displayName("测试")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("unchecked")
    public void testConfigBuilderToolClassValidation() {
        // 测试工具类没有实现 IPanTool 接口，期望抛出 IllegalArgumentException
        // 使用类型转换绕过编译器检查，测试运行时验证
        Class<? extends IPanTool> invalidClass = (Class<? extends IPanTool>) (Class<?>) InvalidTool.class;
        CustomParserConfig.builder()
                .type("test")
                .displayName("测试")
                .toolClass(invalidClass)
                .build();
    }

    @Test
    public void testConfigBuilderRegexValidationMissingKey() {
        // 测试正则表达式缺少KEY命名捕获组，期望抛出 IllegalArgumentException
        try {
            CustomParserConfig config = CustomParserConfig.builder()
                    .type("test")
                    .displayName("测试")
                    .toolClass(TestPanTool.class)
                    .matchPattern("https://test\\.com/s/(.+)")  // 缺少 (?<KEY>)
                    .build();
            
            // 如果没有抛出异常，检查配置
            System.out.println("Pattern: " + config.getMatchPattern().pattern());
            System.out.println("Supports fromShareUrl: " + config.supportsFromShareUrl());
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // 期望抛出异常
            assertTrue(e.getMessage().contains("正则表达式必须包含命名捕获组 KEY"));
        }
    }

    @Test
    public void testConfigBuilderRegexValidationWithKey() {
        // 测试正则表达式包含KEY命名捕获组，应该成功
        CustomParserConfig config = CustomParserConfig.builder()
                .type("test")
                .displayName("测试")
                .toolClass(TestPanTool.class)
                .matchPattern("https://test\\.com/s/(?<KEY>.+)")
                .build();
        
        assertNotNull(config);
        assertTrue(config.supportsFromShareUrl());
        assertEquals("https://test\\.com/s/(?<KEY>.+)", config.getMatchPattern().pattern());
    }

    @Test
    public void testConfigBuilderRegexValidationWithKeyAndPwd() {
        // 测试正则表达式包含KEY和PWD命名捕获组，应该成功
        CustomParserConfig config = CustomParserConfig.builder()
                .type("test")
                .displayName("测试")
                .toolClass(TestPanTool.class)
                .matchPattern("https://test\\.com/s/(?<KEY>.+)(\\?pwd=(?<PWD>.+))?")
                .build();
        
        assertNotNull(config);
        assertTrue(config.supportsFromShareUrl());
    }

    /**
     * 测试用的解析器实现
     */
    public static class TestPanTool implements IPanTool {
        private final ShareLinkInfo shareLinkInfo;

        public TestPanTool(ShareLinkInfo shareLinkInfo) {
            this.shareLinkInfo = shareLinkInfo;
        }

        @Override
        public Future<String> parse() {
            Promise<String> promise = Promise.promise();
            
            String shareKey = shareLinkInfo.getShareKey();
            String password = shareLinkInfo.getSharePassword();
            
            String url = "https://testpan.com/download/" + shareKey;
            if (password != null && !password.isEmpty()) {
                url += "?pwd=" + password;
            }
            
            promise.complete(url);
            return promise.future();
        }
    }

    /**
     * 无效的工具类（未实现 IPanTool 接口）
     */
    public static class InvalidTool {
        public InvalidTool(ShareLinkInfo shareLinkInfo) {
        }
    }
}

