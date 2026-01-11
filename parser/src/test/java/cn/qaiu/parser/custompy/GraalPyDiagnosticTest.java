package cn.qaiu.parser.custompy;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.python.embedding.utils.GraalPyResources;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;

/**
 * 简单的 GraalPy 诊断测试
 */
public class GraalPyDiagnosticTest {
    
    private static final Logger log = LoggerFactory.getLogger(GraalPyDiagnosticTest.class);
    
    @Test
    public void diagnoseClaspath() {
        log.info("==== 诊断 Classpath 和 VFS 资源 ====");
        
        // 1. 检查 classpath
        String classpath = System.getProperty("java.class.path");
        log.info("Java classpath: {}", classpath);
        
        // 2. 检查当前工作目录
        String workingDir = System.getProperty("user.dir");
        log.info("Working directory: {}", workingDir);
        
        // 3. 检查 VFS 资源
        ClassLoader cl = getClass().getClassLoader();
        
        URL vfsVenv = cl.getResource("org.graalvm.python.vfs/venv");
        URL vfsHome = cl.getResource("org.graalvm.python.vfs/home");
        URL vfsRoot = cl.getResource("org.graalvm.python.vfs");
        
        log.info("VFS venv resource: {}", vfsVenv);
        log.info("VFS home resource: {}", vfsHome);
        log.info("VFS root resource: {}", vfsRoot);
        
        if (vfsVenv != null) {
            log.info("✓ VFS venv 资源存在");
            
            // 检查 site-packages
            URL sitePackages = cl.getResource("org.graalvm.python.vfs/venv/lib/python3.11/site-packages");
            log.info("site-packages resource: {}", sitePackages);
            
            URL requestsPkg = cl.getResource("org.graalvm.python.vfs/venv/lib/python3.11/site-packages/requests");
            log.info("requests package resource: {}", requestsPkg);
            
            if (requestsPkg != null) {
                log.info("✓ requests 包资源存在");
            } else {
                log.error("✗ requests 包资源不存在");
            }
        } else {
            log.error("✗ VFS venv 资源不存在");
            
            // 检查是否在文件系统中
            String[] possiblePaths = {
                "target/classes/org.graalvm.python.vfs/venv",
                "../parser/target/classes/org.graalvm.python.vfs/venv",
                "parser/target/classes/org.graalvm.python.vfs/venv"
            };
            
            for (String path : possiblePaths) {
                File file = new File(path);
                log.info("Checking file path {}: exists={}", path, file.exists());
            }
        }
        
        // 4. 尝试创建 Context（不导入任何包）
        try (Context context = GraalPyResources.contextBuilder()
                .allowIO(IOAccess.ALL)
                .allowNativeAccess(true)
                .allowHostAccess(HostAccess.ALL)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            
            log.info("✓ GraalPyResources Context 创建成功");
            
            // 检查 sys.path
            try {
                Value sysPath = context.eval("python", """
                    import sys
                    list(sys.path)
                    """);
                log.info("Python sys.path: {}", sysPath);
            } catch (Exception e) {
                log.error("获取 sys.path 失败", e);
            }
            
        } catch (Exception e) {
            log.error("Context 创建失败", e);
        }
    }
    
    @Test 
    public void testDirectVFSPath() {
        log.info("==== 测试直接指定 VFS 路径 ====");
        
        // 检查可能的 VFS 路径
        String[] vfsPaths = {
            "target/classes/org.graalvm.python.vfs",
            "../parser/target/classes/org.graalvm.python.vfs", 
            "parser/target/classes/org.graalvm.python.vfs"
        };
        
        for (String vfsPath : vfsPaths) {
            File vfsDir = new File(vfsPath);
            if (vfsDir.exists()) {
                log.info("找到 VFS 目录: {}", vfsDir.getAbsolutePath());
                
                File venvDir = new File(vfsDir, "venv");
                File homeDir = new File(vfsDir, "home"); 
                
                log.info("  venv 存在: {}", venvDir.exists());
                log.info("  home 存在: {}", homeDir.exists());
                
                if (venvDir.exists()) {
                    File sitePackages = new File(venvDir, "lib/python3.11/site-packages");
                    if (sitePackages.exists()) {
                        log.info("  site-packages 存在: {}", sitePackages.getAbsolutePath());
                        
                        File requestsDir = new File(sitePackages, "requests");
                        log.info("  requests 目录存在: {}", requestsDir.exists());
                        
                        if (requestsDir.exists()) {
                            String[] files = requestsDir.list();
                            log.info("  requests 目录内容: {}", files != null ? java.util.Arrays.toString(files) : "null");
                        }
                    }
                }
            } else {
                log.info("VFS 目录不存在: {}", vfsPath);
            }
        }
    }
}