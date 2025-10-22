package cn.qaiu.parser.customjs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.qaiu.parser.custom.CustomParserConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * JavaScript脚本加载器
 * 自动加载资源目录和外部目录的JavaScript脚本文件
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/17
 */
public class JsScriptLoader {
    
    private static final Logger log = LoggerFactory.getLogger(JsScriptLoader.class);
    
    private static final String RESOURCE_PATH = "custom-parsers";
    private static final String EXTERNAL_PATH = "./custom-parsers";
    
    // 系统属性配置的外部目录路径
    private static final String EXTERNAL_PATH_PROPERTY = "parser.custom-parsers.path";
    
    /**
     * 加载所有JavaScript脚本
     * @return 解析器配置列表
     */
    public static List<CustomParserConfig> loadAllScripts() {
        List<CustomParserConfig> configs = new ArrayList<>();
        
        // 1. 加载资源目录下的JS文件
        try {
            List<CustomParserConfig> resourceConfigs = loadFromResources();
            configs.addAll(resourceConfigs);
            log.info("从资源目录加载了 {} 个JavaScript解析器", resourceConfigs.size());
        } catch (Exception e) {
            log.warn("从资源目录加载JavaScript脚本失败", e);
        }
        
        // 2. 加载外部目录下的JS文件
        try {
            List<CustomParserConfig> externalConfigs = loadFromExternal();
            configs.addAll(externalConfigs);
            log.info("从外部目录加载了 {} 个JavaScript解析器", externalConfigs.size());
        } catch (Exception e) {
            log.warn("从外部目录加载JavaScript脚本失败", e);
        }
        
        log.info("总共加载了 {} 个JavaScript解析器", configs.size());
        return configs;
    }
    
    /**
     * 从资源目录加载JavaScript脚本
     */
    private static List<CustomParserConfig> loadFromResources() {
        List<CustomParserConfig> configs = new ArrayList<>();
        
        try {
            // 尝试使用反射方式获取JAR包内的资源文件列表
            List<String> resourceFiles = getResourceFileList();
            
            // 按文件名排序，确保加载顺序一致
            resourceFiles.sort(String::compareTo);
            
            for (String resourceFile : resourceFiles) {
                try {
                    InputStream inputStream = JsScriptLoader.class.getClassLoader()
                            .getResourceAsStream(resourceFile);
                    
                    if (inputStream != null) {
                        String jsCode = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                        CustomParserConfig config = JsScriptMetadataParser.parseScript(jsCode);
                        configs.add(config);
                        
                        String fileName = resourceFile.substring(resourceFile.lastIndexOf('/') + 1);
                        log.debug("从资源目录加载脚本: {}", fileName);
                    }
                } catch (Exception e) {
                    log.warn("加载资源脚本失败: {}", resourceFile, e);
                }
            }
            
        } catch (Exception e) {
            log.error("从资源目录加载脚本时发生异常", e);
        }
        
        return configs;
    }
    
    /**
     * 尝试使用反射方式获取JAR包内的资源文件列表
     */
    private static List<String> getResourceFileList() {
        List<String> resourceFiles = new ArrayList<>();
        
        try {
            // 尝试获取资源目录的URL
            java.net.URL resourceUrl = JsScriptLoader.class.getClassLoader()
                    .getResource(RESOURCE_PATH);
            
            if (resourceUrl != null) {
                String protocol = resourceUrl.getProtocol();
                
                if ("jar".equals(protocol)) {
                    // JAR包内的资源
                    resourceFiles = getJarResourceFiles(resourceUrl);
                } else if ("file".equals(protocol)) {
                    // 文件系统中的资源（开发环境）
                    resourceFiles = getFileSystemResourceFiles(resourceUrl);
                }
            }
        } catch (Exception e) {
            log.debug("使用反射方式获取资源文件列表失败，将使用预定义列表", e);
        }
        
        return resourceFiles;
    }
    
    /**
     * 获取JAR包内的资源文件列表
     */
    private static List<String> getJarResourceFiles(java.net.URL jarUrl) {
        List<String> resourceFiles = new ArrayList<>();
        
        try {
            String jarPath = jarUrl.getPath().substring(5, jarUrl.getPath().indexOf("!"));
            java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath);
            
            java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                if (entryName.startsWith(RESOURCE_PATH + "/") && 
                    entryName.endsWith(".js") && 
                    !isExcludedFile(entryName.substring(entryName.lastIndexOf('/') + 1))) {
                    resourceFiles.add(entryName);
                }
            }
            
            jarFile.close();
        } catch (Exception e) {
            log.debug("解析JAR包资源文件失败", e);
        }
        
        return resourceFiles;
    }
    
    /**
     * 获取文件系统中的资源文件列表
     */
    private static List<String> getFileSystemResourceFiles(java.net.URL fileUrl) {
        List<String> resourceFiles = new ArrayList<>();
        
        try {
            java.io.File resourceDir = new java.io.File(fileUrl.getPath());
            if (resourceDir.exists() && resourceDir.isDirectory()) {
                java.io.File[] files = resourceDir.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        if (file.isFile() && file.getName().endsWith(".js") && 
                            !isExcludedFile(file.getName())) {
                            resourceFiles.add(RESOURCE_PATH + "/" + file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("解析文件系统资源文件失败", e);
        }
        
        return resourceFiles;
    }
    
    
    /**
     * 从外部目录加载JavaScript脚本
     */
    private static List<CustomParserConfig> loadFromExternal() {
        List<CustomParserConfig> configs = new ArrayList<>();
        
        try {
            // 获取外部目录路径，支持系统属性配置
            String externalPath = getExternalPath();
            Path externalDir = Paths.get(externalPath);
            
            if (!Files.exists(externalDir) || !Files.isDirectory(externalDir)) {
                log.debug("外部目录 {} 不存在或不是目录", externalPath);
                return configs;
            }
            
            try (Stream<Path> paths = Files.walk(externalDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".js"))
                        .filter(path -> !isExcludedFile(path.getFileName().toString()))
                        .forEach(path -> {
                            try {
                                String jsCode = Files.readString(path, StandardCharsets.UTF_8);
                                CustomParserConfig config = JsScriptMetadataParser.parseScript(jsCode);
                                configs.add(config);
                                log.debug("从外部目录加载脚本: {}", path.getFileName());
                            } catch (Exception e) {
                                log.warn("加载外部脚本失败: {}", path.getFileName(), e);
                            }
                        });
            }
            
        } catch (Exception e) {
            log.error("从外部目录加载脚本时发生异常", e);
        }
        
        return configs;
    }
    
    /**
     * 获取外部目录路径
     * 优先级：系统属性 > 环境变量 > 默认路径
     */
    private static String getExternalPath() {
        // 1. 检查系统属性
        String systemProperty = System.getProperty(EXTERNAL_PATH_PROPERTY);
        if (systemProperty != null && !systemProperty.trim().isEmpty()) {
            log.debug("使用系统属性配置的外部目录: {}", systemProperty);
            return systemProperty;
        }
        
        // 2. 检查环境变量
        String envVariable = System.getenv("PARSER_CUSTOM_PARSERS_PATH");
        if (envVariable != null && !envVariable.trim().isEmpty()) {
            log.debug("使用环境变量配置的外部目录: {}", envVariable);
            return envVariable;
        }
        
        // 3. 使用默认路径
        log.debug("使用默认外部目录: {}", EXTERNAL_PATH);
        return EXTERNAL_PATH;
    }
    
    /**
     * 从指定文件加载JavaScript脚本
     * @param filePath 文件路径
     * @return 解析器配置
     */
    public static CustomParserConfig loadFromFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("文件不存在: " + filePath);
            }
            
            String jsCode = Files.readString(path, StandardCharsets.UTF_8);
            return JsScriptMetadataParser.parseScript(jsCode);
            
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + filePath, e);
        }
    }
    
    /**
     * 从指定文件加载JavaScript脚本（资源路径）
     * @param resourcePath 资源路径
     * @return 解析器配置
     */
    public static CustomParserConfig loadFromResource(String resourcePath) {
        try {
            InputStream inputStream = JsScriptLoader.class.getClassLoader()
                    .getResourceAsStream(resourcePath);
            
            if (inputStream == null) {
                throw new IllegalArgumentException("资源文件不存在: " + resourcePath);
            }
            
            String jsCode = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return JsScriptMetadataParser.parseScript(jsCode);
            
        } catch (IOException e) {
            throw new RuntimeException("读取资源文件失败: " + resourcePath, e);
        }
    }
    
    /**
     * 检查外部目录是否存在
     * @return true表示存在，false表示不存在
     */
    public static boolean isExternalDirectoryExists() {
        Path externalDir = Paths.get(EXTERNAL_PATH);
        return Files.exists(externalDir) && Files.isDirectory(externalDir);
    }
    
    /**
     * 创建外部目录
     * @return true表示创建成功，false表示创建失败
     */
    public static boolean createExternalDirectory() {
        try {
            Path externalDir = Paths.get(EXTERNAL_PATH);
            Files.createDirectories(externalDir);
            log.info("创建外部目录成功: {}", EXTERNAL_PATH);
            return true;
        } catch (IOException e) {
            log.error("创建外部目录失败: {}", EXTERNAL_PATH, e);
            return false;
        }
    }
    
    /**
     * 获取外部目录路径
     * @return 外部目录路径
     */
    public static String getExternalDirectoryPath() {
        return EXTERNAL_PATH;
    }
    
    /**
     * 获取资源目录路径
     * @return 资源目录路径
     */
    public static String getResourceDirectoryPath() {
        return RESOURCE_PATH;
    }
    
    /**
     * 检查文件是否应该被排除
     * @param fileName 文件名
     * @return true表示应该排除，false表示应该加载
     */
    private static boolean isExcludedFile(String fileName) {
        // 排除类型定义文件和其他非解析器文件
        return fileName.equals("types.js") || 
               fileName.equals("jsconfig.json") ||
               fileName.equals("README.md") ||
               fileName.contains(".test.") ||
               fileName.contains(".spec.");
    }
}
