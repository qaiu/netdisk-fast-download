package cn.qaiu.parser.custompy;

import org.graalvm.polyglot.Context;
import org.graalvm.python.embedding.utils.GraalPyResources;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

/**
 * GraalPy Context 创建测试
 */
public class GraalPyContextTest {
    
    private static final Logger log = LoggerFactory.getLogger(GraalPyContextTest.class);
    
    @Test
    public void testBasicContextCreation() {
        log.info("==== 测试基础 Context 创建 ====");
        
        try {
            // 检查 VFS 资源
            var vfsResource = getClass().getClassLoader().getResource("org.graalvm.python.vfs/venv");
            var homeResource = getClass().getClassLoader().getResource("org.graalvm.python.vfs/home");
            log.info("VFS资源检查:");
            log.info("  venv: {}", vfsResource != null ? "存在 -> " + vfsResource : "不存在");
            log.info("  home: {}", homeResource != null ? "存在 -> " + homeResource : "不存在");
            
            // 使用 GraalPyResources 创建 Context
            log.info("创建 GraalPyResources Context...");
            
            try (Context ctx = GraalPyResources.contextBuilder().build()) {
                log.info("✓ Context 创建成功");
                
                // 简单的 Python 测试
                ctx.eval("python", "print('Hello from GraalPy!')");
                log.info("✓ Python 执行成功");
                
                // 测试 sys.path
                ctx.eval("python", """
                    import sys
                    print("sys.path:")
                    for p in sys.path[:5]:
                        print(f"  {p}")
                    """);
                
                // 尝试导入 requests
                try {
                    ctx.eval("python", "import requests");
                    log.info("✓ requests 导入成功");
                    
                    var version = ctx.eval("python", "requests.__version__");
                    log.info("✓ requests 版本: {}", version.asString());
                } catch (Exception e) {
                    log.warn("requests 导入失败: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("测试失败", e);
            fail("测试失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testPoolContextCreation() {
        log.info("==== 测试 PyContextPool Context 创建 ====");
        
        try {
            PyContextPool pool = PyContextPool.getInstance();
            log.info("PyContextPool 实例获取成功");
            
            try (Context ctx = pool.createFreshContext()) {
                log.info("✓ FreshContext 创建成功");
                
                // 简单 Python 测试
                ctx.eval("python", "print('Hello from Pool Context!')");
                log.info("✓ Python 执行成功");
                
            }
        } catch (Exception e) {
            log.error("测试失败", e);
            e.printStackTrace();
            fail("测试失败: " + e.getMessage());
        }
    }
}
