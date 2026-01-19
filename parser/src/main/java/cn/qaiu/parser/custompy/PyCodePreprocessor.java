package cn.qaiu.parser.custompy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Python代码预处理器
 * 用于在运行时自动检测代码中的网络请求导入，并动态注入requests_guard猴子补丁
 * 
 * 功能：
 * 1. 检测代码中是否导入了 requests、urllib、httpx 等网络请求库
 * 2. 如果检测到网络请求库，自动在代码头部注入 requests_guard 猴子补丁
 * 3. 生成日志信息供演练场控制台显示
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class PyCodePreprocessor {
    
    private static final Logger log = LoggerFactory.getLogger(PyCodePreprocessor.class);
    
    // 检测网络请求库的正则表达式
    private static final Pattern IMPORT_REQUESTS = Pattern.compile(
            "^\\s*(?:import\\s+requests|from\\s+requests\\b)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern IMPORT_URLLIB = Pattern.compile(
            "^\\s*(?:import\\s+urllib|from\\s+urllib\\b)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern IMPORT_HTTPX = Pattern.compile(
            "^\\s*(?:import\\s+httpx|from\\s+httpx\\b)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern IMPORT_AIOHTTP = Pattern.compile(
            "^\\s*(?:import\\s+aiohttp|from\\s+aiohttp\\b)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern IMPORT_SOCKET = Pattern.compile(
            "^\\s*(?:import\\s+socket|from\\s+socket\\b)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );
    
    /**
     * 预处理Python代码 - 检测并注入猴子补丁
     * 
     * @param originalCode 原始Python代码
     * @return 处理后的代码（可能包含注入的补丁）
     */
    public static PyPreprocessResult preprocess(String originalCode) {
        if (originalCode == null || originalCode.trim().isEmpty()) {
            return new PyPreprocessResult(originalCode, false, null, "代码为空，无需预处理");
        }
        
        // 检测网络请求库
        NetworkLibraryDetection detection = detectNetworkLibraries(originalCode);
        
        if (detection.hasAnyNetworkLibrary()) {
            log.debug("检测到网络请求库: {}", detection.getDetectedLibraries());
            
            // 加载猴子补丁代码
            String patchCode = loadRequestsGuardPatch();
            
            if (patchCode != null && !patchCode.isEmpty()) {
                // 在代码头部注入补丁
                String preprocessedCode = injectPatch(originalCode, patchCode);
                
                String logMessage = String.format(
                        "✓ 网络请求安全拦截已启用 (检测到: %s) | 已动态注入 requests_guard 猴子补丁",
                        detection.getDetectedLibrariesAsString()
                );
                
                log.info(logMessage);
                return new PyPreprocessResult(
                        preprocessedCode, 
                        true, 
                        detection.getDetectedLibraries(),
                        logMessage
                );
            } else {
                String logMessage = "⚠ 检测到网络请求库但猴子补丁加载失败，请检查资源文件";
                log.warn(logMessage);
                return new PyPreprocessResult(
                        originalCode,
                        false,
                        detection.getDetectedLibraries(),
                        logMessage
                );
            }
        } else {
            // 没有检测到网络请求库
            String logMessage = "ℹ 代码中未检测到网络请求库，不需要注入安全拦截补丁";
            log.debug(logMessage);
            return new PyPreprocessResult(originalCode, false, null, logMessage);
        }
    }
    
    /**
     * 检测代码中使用的网络请求库
     */
    private static NetworkLibraryDetection detectNetworkLibraries(String code) {
        NetworkLibraryDetection detection = new NetworkLibraryDetection();
        
        if (IMPORT_REQUESTS.matcher(code).find()) {
            detection.addLibrary("requests");
        }
        if (IMPORT_URLLIB.matcher(code).find()) {
            detection.addLibrary("urllib");
        }
        if (IMPORT_HTTPX.matcher(code).find()) {
            detection.addLibrary("httpx");
        }
        if (IMPORT_AIOHTTP.matcher(code).find()) {
            detection.addLibrary("aiohttp");
        }
        if (IMPORT_SOCKET.matcher(code).find()) {
            detection.addLibrary("socket");
        }
        
        return detection;
    }
    
    /**
     * 加载requests_guard猴子补丁代码
     */
    private static String loadRequestsGuardPatch() {
        try {
            // 从资源文件加载requests_guard.py
            InputStream inputStream = PyCodePreprocessor.class.getClassLoader()
                    .getResourceAsStream("requests_guard.py");
            
            if (inputStream == null) {
                log.warn("无法找到 requests_guard.py 资源文件");
                return null;
            }
            
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            return content.toString();
        } catch (IOException e) {
            log.error("加载 requests_guard.py 失败", e);
            return null;
        }
    }
    
    /**
     * 在Python代码头部注入补丁
     * 
     * @param originalCode 原始代码
     * @param patchCode 补丁代码
     * @return 注入补丁后的代码
     */
    private static String injectPatch(String originalCode, String patchCode) {
        // 找到第一个非注释、非空行作为注入位置
        String[] lines = originalCode.split("\n");
        int insertIndex = 0;
        
        // 跳过模块文档字符串和注释
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // 跳过空行和注释
            if (line.isEmpty() || line.startsWith("#")) {
                insertIndex = i + 1;
                continue;
            }
            
            // 跳过模块文档字符串 (""" 或 ''')
            if (line.startsWith("\"\"\"") || line.startsWith("'''")) {
                // 简单处理：假设文档字符串在单行内或下一行结束
                insertIndex = i + 1;
                if (line.length() > 3 && !line.endsWith(line.substring(0, 3))) {
                    continue; // 多行文档字符串，继续跳过
                }
            }
            
            // 找到第一个有效的代码行
            break;
        }
        
        // 构建注入后的代码
        StringBuilder result = new StringBuilder();
        
        // 添加前面的行
        for (int i = 0; i < insertIndex && i < lines.length; i++) {
            result.append(lines[i]).append("\n");
        }
        
        // 添加补丁代码
        result.append("\n# ===== 自动注入的网络请求安全补丁 (由 PyCodePreprocessor 生成) =====\n");
        result.append(patchCode);
        result.append("\n# ===== 安全补丁结束 =====\n\n");
        
        // 添加剩余的代码
        for (int i = insertIndex; i < lines.length; i++) {
            result.append(lines[i]);
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }
        
        return result.toString();
    }
    
    /**
     * 预处理结果类
     */
    public static class PyPreprocessResult {
        private final String processedCode;      // 处理后的代码
        private final boolean patchInjected;     // 是否注入了补丁
        private final java.util.List<String> detectedLibraries;  // 检测到的库
        private final String logMessage;         // 日志消息
        
        public PyPreprocessResult(String processedCode, boolean patchInjected, 
                                java.util.List<String> detectedLibraries, String logMessage) {
            this.processedCode = processedCode;
            this.patchInjected = patchInjected;
            this.detectedLibraries = detectedLibraries;
            this.logMessage = logMessage;
        }
        
        public String getProcessedCode() {
            return processedCode;
        }
        
        public boolean isPatchInjected() {
            return patchInjected;
        }
        
        public java.util.List<String> getDetectedLibraries() {
            return detectedLibraries;
        }
        
        public String getLogMessage() {
            return logMessage;
        }
    }
    
    /**
     * 网络库检测结果
     */
    private static class NetworkLibraryDetection {
        private final java.util.List<String> detectedLibraries = new java.util.ArrayList<>();
        
        void addLibrary(String library) {
            if (!detectedLibraries.contains(library)) {
                detectedLibraries.add(library);
            }
        }
        
        boolean hasAnyNetworkLibrary() {
            return !detectedLibraries.isEmpty();
        }
        
        java.util.List<String> getDetectedLibraries() {
            return detectedLibraries;
        }
        
        String getDetectedLibrariesAsString() {
            return String.join(", ", detectedLibraries);
        }
    }
}
