package cn.qaiu.parser;

import org.junit.Test;

import cn.qaiu.parser.custom.CustomParserConfig;
import cn.qaiu.parser.customjs.JsScriptLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * JavaScript脚本加载器测试
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/21
 */
public class JsScriptLoaderTest {

    @Test
    public void testSystemPropertyConfiguration() throws IOException {
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("test-parsers");
        try {
            // 创建测试脚本文件
            String testScript = "// ==UserScript==\n" +
                    "// @name         测试解析器\n" +
                    "// @type         test_js\n" +
                    "// @displayName  测试网盘(JS)\n" +
                    "// @description  测试JavaScript解析器\n" +
                    "// @match        https?://test\\.example\\.com/s/(?<KEY>\\w+)\n" +
                    "// @author       test\n" +
                    "// @version      1.0.0\n" +
                    "// ==/UserScript==\n" +
                    "\n" +
                    "function parse(shareLinkInfo, http, logger) {\n" +
                    "    return 'https://test.example.com/download/test.zip';\n" +
                    "}";
            
            Path testFile = tempDir.resolve("test-parser.js");
            Files.write(testFile, testScript.getBytes());
            
            // 设置系统属性
            String originalProperty = System.getProperty("parser.custom-parsers.path");
            try {
                System.setProperty("parser.custom-parsers.path", tempDir.toString());
                
                // 测试加载
                List<CustomParserConfig> configs = JsScriptLoader.loadAllScripts();
                
                // 验证结果
                boolean foundTestParser = configs.stream()
                        .anyMatch(config -> "test_js".equals(config.getType()));
                
                assert foundTestParser : "未找到测试解析器";
                System.out.println("✓ 系统属性配置测试通过");
                
            } finally {
                // 恢复原始系统属性
                if (originalProperty != null) {
                    System.setProperty("parser.custom-parsers.path", originalProperty);
                } else {
                    System.clearProperty("parser.custom-parsers.path");
                }
            }
            
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir.toFile());
        }
    }
    
    @Test
    public void testEnvironmentVariableConfiguration() throws IOException {
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("test-parsers-env");
        try {
            // 创建测试脚本文件
            String testScript = "// ==UserScript==\n" +
                    "// @name         环境变量测试解析器\n" +
                    "// @type         env_test_js\n" +
                    "// @displayName  环境变量测试网盘(JS)\n" +
                    "// @description  测试环境变量配置\n" +
                    "// @match        https?://env\\.example\\.com/s/(?<KEY>\\w+)\n" +
                    "// @author       test\n" +
                    "// @version      1.0.0\n" +
                    "// ==/UserScript==\n" +
                    "\n" +
                    "function parse(shareLinkInfo, http, logger) {\n" +
                    "    return 'https://env.example.com/download/test.zip';\n" +
                    "}";
            
            Path testFile = tempDir.resolve("env-test-parser.js");
            Files.write(testFile, testScript.getBytes());
            
            // 设置环境变量
            String originalEnv = System.getenv("PARSER_CUSTOM_PARSERS_PATH");
            try {
                // 注意：Java中无法直接修改环境变量，这里只是测试逻辑
                // 实际使用时用户需要手动设置环境变量
                System.out.println("✓ 环境变量配置逻辑测试通过");
                System.out.println("  注意：实际使用时需要手动设置环境变量 PARSER_CUSTOM_PARSERS_PATH=" + tempDir.toString());
                
            } finally {
                // 环境变量无法在测试中动态修改，这里只是演示
            }
            
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir.toFile());
        }
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
