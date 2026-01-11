package cn.qaiu.parser.custompy;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.python.embedding.utils.GraalPyResources;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

/**
 * 手动配置 Python 路径的测试
 */
public class GraalPyManualPathTest {
    
    private static final Logger log = LoggerFactory.getLogger(GraalPyManualPathTest.class);
    
    @Test
    public void testManualPythonPath() {
        log.info("==== 测试手动配置 Python 路径 ====");
        
        try (Context context = GraalPyResources.contextBuilder()
                .allowIO(IOAccess.ALL)
                .allowNativeAccess(true)
                .allowHostAccess(HostAccess.ALL)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            
            log.info("Context 创建成功");
            
            // 手动添加 site-packages 到 sys.path
            String addPathScript = """
                import sys
                import os
                
                # 尝试多个可能的路径
                possible_paths = [
                    'target/classes/org.graalvm.python.vfs/venv/lib/python3.11/site-packages',
                    '../parser/target/classes/org.graalvm.python.vfs/venv/lib/python3.11/site-packages',
                    'parser/target/classes/org.graalvm.python.vfs/venv/lib/python3.11/site-packages'
                ]
                
                added_paths = []
                for path in possible_paths:
                    if os.path.exists(path):
                        abs_path = os.path.abspath(path)
                        if abs_path not in sys.path:
                            sys.path.insert(0, abs_path)
                            added_paths.append(abs_path)
                
                # 也尝试从 classpath 资源路径
                import importlib.util
                
                # 打印当前路径信息
                print(f"Working directory: {os.getcwd()}")
                print(f"Python sys.path: {sys.path[:5]}")  # 只打印前5个
                print(f"Added paths: {added_paths}")
                
                len(added_paths)
                """;
            
            Value result = context.eval("python", addPathScript);
            int addedPaths = result.asInt();
            log.info("手动添加了 {} 个路径", addedPaths);
            
            if (addedPaths > 0) {
                // 现在尝试导入 requests
                try {
                    context.eval("python", "import requests");
                    log.info("✓ 手动配置路径后 requests 导入成功");
                    
                    Value version = context.eval("python", "requests.__version__");
                    log.info("requests 版本: {}", version.asString());
                    
                    assertTrue("requests 应该能够成功导入", true);
                    
                } catch (Exception e) {
                    log.error("即使手动添加路径，requests 导入仍然失败", e);
                    
                    // 检查路径中是否有 requests 目录
                    Value checkDirs = context.eval("python", """
                        import os
                        import sys
                        
                        found_requests = []
                        for path in sys.path:
                            requests_path = os.path.join(path, 'requests')
                            if os.path.exists(requests_path) and os.path.isdir(requests_path):
                                found_requests.append(requests_path)
                        
                        found_requests
                        """);
                    log.info("找到的 requests 目录: {}", checkDirs);
                    
                    fail("手动配置路径后仍无法导入 requests: " + e.getMessage());
                }
            } else {
                log.warn("未找到有效的 site-packages 路径，跳过 requests 导入测试");
            }
            
        } catch (Exception e) {
            log.error("测试失败", e);
            fail("测试异常: " + e.getMessage());
        }
    }
    
    @Test
    public void testRequestsWithAbsolutePath() {
        log.info("==== 测试使用绝对路径导入 requests ====");
        
        // 获取当前工作目录
        String workDir = System.getProperty("user.dir");
        log.info("当前工作目录: {}", workDir);
        
        // 构造绝对路径
        String vfsPath = workDir + "/target/classes/org.graalvm.python.vfs/venv/lib/python3.11/site-packages";
        java.io.File vfsFile = new java.io.File(vfsPath);
        
        if (!vfsFile.exists()) {
            // 尝试上级目录（可能在子模块中运行）
            vfsPath = workDir + "/../parser/target/classes/org.graalvm.python.vfs/venv/lib/python3.11/site-packages";
            vfsFile = new java.io.File(vfsPath);
        }
        
        if (!vfsFile.exists()) {
            log.warn("找不到 VFS site-packages 目录，跳过测试");
            return;
        }
        
        log.info("使用 VFS 路径: {}", vfsFile.getAbsolutePath());
        
        try (Context context = GraalPyResources.contextBuilder()
                .allowIO(IOAccess.ALL)
                .allowNativeAccess(true)
                .allowHostAccess(HostAccess.ALL)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            
            // 直接设置绝对路径
            context.getBindings("python").putMember("vfs_site_packages", vfsFile.getAbsolutePath());
            
            String script = """
                import sys
                import os
                
                # 添加 VFS site-packages 到 sys.path
                vfs_path = vfs_site_packages
                if os.path.exists(vfs_path) and vfs_path not in sys.path:
                    sys.path.insert(0, vfs_path)
                    print(f"Added VFS path: {vfs_path}")
                
                # 检查 requests 目录
                requests_dir = os.path.join(vfs_path, 'requests')
                requests_exists = os.path.exists(requests_dir)
                print(f"Requests directory exists: {requests_exists}")
                
                if requests_exists:
                    print(f"Requests dir contents: {os.listdir(requests_dir)[:5]}")
                
                requests_exists
                """;
            
            Value requestsExists = context.eval("python", script);
            
            if (requestsExists.asBoolean()) {
                log.info("✓ requests 目录存在，尝试导入");
                
                try {
                    context.eval("python", "import requests");
                    log.info("✓ 使用绝对路径成功导入 requests");
                    
                    Value version = context.eval("python", "requests.__version__");
                    log.info("requests 版本: {}", version.asString());
                    
                } catch (Exception e) {
                    log.error("使用绝对路径导入 requests 失败", e);
                    
                    // 获取详细错误信息
                    try {
                        Value errorInfo = context.eval("python", """
                            import sys
                            import traceback
                            
                            try:
                                import requests
                            except Exception as e:
                                error_info = {
                                    'type': type(e).__name__,
                                    'message': str(e),
                                    'traceback': traceback.format_exc()
                                }
                                error_info
                            """);
                        log.error("Python 导入错误详情: {}", errorInfo);
                    } catch (Exception te) {
                        log.error("无法获取 Python 错误详情", te);
                    }
                    
                    throw e;
                }
            } else {
                fail("requests 目录不存在于 VFS 路径中");
            }
            
        } catch (Exception e) {
            log.error("绝对路径测试失败", e);
            fail("测试失败: " + e.getMessage());
        }
    }
}