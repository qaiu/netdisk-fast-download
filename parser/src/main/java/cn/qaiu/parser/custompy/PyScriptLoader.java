package cn.qaiu.parser.custompy;

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
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Python脚本加载器
 * 自动加载资源目录和外部目录的Python脚本文件
 *
 * @author QAIU
 */
public class PyScriptLoader {
    
    private static final Logger log = LoggerFactory.getLogger(PyScriptLoader.class);
    
    private static final String RESOURCE_PATH = "custom-parsers/py";
    private static final String EXTERNAL_PATH = "./custom-parsers/py";
    
    // 系统属性配置的外部目录路径
    private static final String EXTERNAL_PATH_PROPERTY = "parser.custom-parsers.py.path";
    
    /**
     * 加载所有Python脚本
     * @return 解析器配置列表
     */
    public static List<CustomParserConfig> loadAllScripts() {
        List<CustomParserConfig> configs = new ArrayList<>();
        
        // 1. 加载资源目录下的Python文件
        try {
            List<CustomParserConfig> resourceConfigs = loadFromResources();
            configs.addAll(resourceConfigs);
            log.info("从资源目录加载了 {} 个Python解析器", resourceConfigs.size());
        } catch (Exception e) {
            log.warn("从资源目录加载Python脚本失败", e);
        }
        
        // 2. 加载外部目录下的Python文件
        try {
            List<CustomParserConfig> externalConfigs = loadFromExternal();
            configs.addAll(externalConfigs);
            log.info("从外部目录加载了 {} 个Python解析器", externalConfigs.size());
        } catch (Exception e) {
            log.warn("从外部目录加载Python脚本失败", e);
        }
        
        log.info("总共加载了 {} 个Python解析器", configs.size());
        return configs;
    }
    
    /**
     * 从资源目录加载Python脚本
     */
    private static List<CustomParserConfig> loadFromResources() {
        List<CustomParserConfig> configs = new ArrayList<>();
        
        try {
            List<String> resourceFiles = getResourceFileList();
            resourceFiles.sort(String::compareTo);
            
            for (String resourceFile : resourceFiles) {
                try {
                    InputStream inputStream = PyScriptLoader.class.getClassLoader()
                            .getResourceAsStream(resourceFile);
                    
                    if (inputStream != null) {
                        String pyCode = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                        CustomParserConfig config = PyScriptMetadataParser.parseScript(pyCode);
                        configs.add(config);
                        
                        String fileName = resourceFile.substring(resourceFile.lastIndexOf('/') + 1);
                        log.debug("从资源目录加载Python脚本: {}", fileName);
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
     * 获取资源目录中的Python文件列表
     */
    private static List<String> getResourceFileList() {
        List<String> resourceFiles = new ArrayList<>();
        
        try {
            java.net.URL resourceUrl = PyScriptLoader.class.getClassLoader()
                    .getResource(RESOURCE_PATH);
            
            if (resourceUrl != null) {
                String protocol = resourceUrl.getProtocol();
                
                if ("jar".equals(protocol)) {
                    resourceFiles = getJarResourceFiles(resourceUrl);
                } else if ("file".equals(protocol)) {
                    resourceFiles = getFileSystemResourceFiles(resourceUrl);
                }
            }
        } catch (Exception e) {
            log.debug("获取资源文件列表失败", e);
        }
        
        return resourceFiles;
    }
    
    /**
     * 获取JAR包内的Python资源文件列表
     */
    private static List<String> getJarResourceFiles(java.net.URL jarUrl) {
        List<String> resourceFiles = new ArrayList<>();
        
        try {
            String jarPath = jarUrl.getPath().substring(5, jarUrl.getPath().indexOf("!"));
            JarFile jarFile = new JarFile(jarPath);
            
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                if (entryName.startsWith(RESOURCE_PATH + "/") && 
                    entryName.endsWith(".py") && 
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
     * 获取文件系统中的Python资源文件列表
     */
    private static List<String> getFileSystemResourceFiles(java.net.URL fileUrl) {
        List<String> resourceFiles = new ArrayList<>();
        
        try {
            java.io.File resourceDir = new java.io.File(fileUrl.getPath());
            if (resourceDir.exists() && resourceDir.isDirectory()) {
                java.io.File[] files = resourceDir.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        if (file.isFile() && file.getName().endsWith(".py") && 
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
     * 从外部目录加载Python脚本
     */
    private static List<CustomParserConfig> loadFromExternal() {
        List<CustomParserConfig> configs = new ArrayList<>();
        
        try {
            String externalPath = getExternalPath();
            Path externalDir = Paths.get(externalPath);
            
            if (!Files.exists(externalDir) || !Files.isDirectory(externalDir)) {
                log.debug("外部目录 {} 不存在或不是目录", externalPath);
                return configs;
            }
            
            try (Stream<Path> paths = Files.walk(externalDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".py"))
                        .filter(path -> !isExcludedFile(path.getFileName().toString()))
                        .forEach(path -> {
                            try {
                                String pyCode = Files.readString(path, StandardCharsets.UTF_8);
                                CustomParserConfig config = PyScriptMetadataParser.parseScript(pyCode);
                                configs.add(config);
                                log.debug("从外部目录加载Python脚本: {}", path.getFileName());
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
     */
    private static String getExternalPath() {
        // 1. 检查系统属性
        String systemProperty = System.getProperty(EXTERNAL_PATH_PROPERTY);
        if (systemProperty != null && !systemProperty.trim().isEmpty()) {
            log.debug("使用系统属性配置的Python外部目录: {}", systemProperty);
            return systemProperty;
        }
        
        // 2. 检查环境变量
        String envVariable = System.getenv("PARSER_CUSTOM_PARSERS_PY_PATH");
        if (envVariable != null && !envVariable.trim().isEmpty()) {
            log.debug("使用环境变量配置的Python外部目录: {}", envVariable);
            return envVariable;
        }
        
        // 3. 使用默认路径
        log.debug("使用默认Python外部目录: {}", EXTERNAL_PATH);
        return EXTERNAL_PATH;
    }
    
    /**
     * 从指定文件加载Python脚本
     * @param filePath 文件路径
     * @return 解析器配置
     */
    public static CustomParserConfig loadFromFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("文件不存在: " + filePath);
            }
            
            String pyCode = Files.readString(path, StandardCharsets.UTF_8);
            return PyScriptMetadataParser.parseScript(pyCode);
            
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + filePath, e);
        }
    }
    
    /**
     * 从指定资源路径加载Python脚本
     * @param resourcePath 资源路径
     * @return 解析器配置
     */
    public static CustomParserConfig loadFromResource(String resourcePath) {
        try {
            InputStream inputStream = PyScriptLoader.class.getClassLoader()
                    .getResourceAsStream(resourcePath);
            
            if (inputStream == null) {
                throw new IllegalArgumentException("资源文件不存在: " + resourcePath);
            }
            
            String pyCode = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return PyScriptMetadataParser.parseScript(pyCode);
            
        } catch (IOException e) {
            throw new RuntimeException("读取资源文件失败: " + resourcePath, e);
        }
    }
    
    /**
     * 检查外部目录是否存在
     */
    public static boolean isExternalDirectoryExists() {
        Path externalDir = Paths.get(EXTERNAL_PATH);
        return Files.exists(externalDir) && Files.isDirectory(externalDir);
    }
    
    /**
     * 创建外部目录
     */
    public static boolean createExternalDirectory() {
        try {
            Path externalDir = Paths.get(EXTERNAL_PATH);
            Files.createDirectories(externalDir);
            log.info("创建Python外部目录成功: {}", EXTERNAL_PATH);
            return true;
        } catch (IOException e) {
            log.error("创建Python外部目录失败: {}", EXTERNAL_PATH, e);
            return false;
        }
    }
    
    /**
     * 获取外部目录路径
     */
    public static String getExternalDirectoryPath() {
        return EXTERNAL_PATH;
    }
    
    /**
     * 获取资源目录路径
     */
    public static String getResourceDirectoryPath() {
        return RESOURCE_PATH;
    }
    
    /**
     * 检查文件是否应该被排除
     */
    private static boolean isExcludedFile(String fileName) {
        return fileName.equals("types.pyi") || 
               fileName.equals("__init__.py") ||
               fileName.equals("README.md") ||
               fileName.contains("_test.") ||
               fileName.contains("_spec.") ||
               fileName.startsWith("test_");
    }
}
