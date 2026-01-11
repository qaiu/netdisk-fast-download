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
 * GraalPy pip 包测试
 * 验证 requests 等 pip 包是否能正常加载和使用
 */
public class GraalPyPipTest {
    
    private static final Logger log = LoggerFactory.getLogger(GraalPyPipTest.class);
    
    @Test
    public void testGraalPyResourcesAvailability() {
        log.info("==== 测试 GraalPy VFS 资源可用性 ====");
        
        // 检查 VFS 资源是否存在
        var vfsVenv = getClass().getClassLoader().getResource("org.graalvm.python.vfs/venv");
        var vfsHome = getClass().getClassLoader().getResource("org.graalvm.python.vfs/home");
        
        log.info("VFS venv 资源: {}", vfsVenv);
        log.info("VFS home 资源: {}", vfsHome);
        
        assertNotNull("VFS venv 资源应该存在", vfsVenv);
        assertNotNull("VFS home 资源应该存在", vfsHome);
        
        log.info("✓ VFS 资源检查通过");
    }
    
    @Test
    public void testGraalPyContextCreation() {
        log.info("==== 测试 GraalPyResources Context 创建 ====");
        
        try (Context context = GraalPyResources.contextBuilder()
                .allowIO(IOAccess.ALL)
                .allowNativeAccess(true)
                .allowHostAccess(HostAccess.ALL)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            
            log.info("✓ GraalPyResources Context 创建成功");
            
            // 测试基本 Python 功能
            Value result = context.eval("python", "2 + 3");
            assertEquals("Python 基本计算", 5, result.asInt());
            
            log.info("✓ Python 基本功能正常");
            
        } catch (Exception e) {
            log.error("GraalPyResources Context 创建失败", e);
            fail("Context 创建失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testPythonBuiltinModules() {
        log.info("==== 测试 Python 内置模块 ====");
        
        try (Context context = GraalPyResources.contextBuilder()
                .allowIO(IOAccess.ALL)
                .allowNativeAccess(true)
                .allowHostAccess(HostAccess.ALL)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            
            // 测试基本内置模块
            context.eval("python", "import sys");
            context.eval("python", "import os");
            context.eval("python", "import json");
            context.eval("python", "import re");
            context.eval("python", "import time");
            context.eval("python", "import random");
            
            log.info("✓ Python 内置模块导入成功");
            
        } catch (Exception e) {
            log.error("Python 内置模块测试失败", e);
            fail("内置模块导入失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testRequestsImport() {
        log.info("==== 测试 requests 包导入 ====");
        
        try (Context context = GraalPyResources.contextBuilder()
                .allowIO(IOAccess.ALL)
                .allowNativeAccess(true)
                .allowHostAccess(HostAccess.ALL)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            
            // 首先检查 sys.path
            Value sysPath = context.eval("python", """
                import sys
                sys.path
                """);
            log.info("Python sys.path: {}", sysPath);
            
            // 检查 site-packages 是否在路径中
            Value sitePackagesCheck = context.eval("python", """
                import sys
                [p for p in sys.path if 'site-packages' in p]
                """);
            log.info("site-packages 路径: {}", sitePackagesCheck);
            
            try {
                // 测试 requests 导入
                context.eval("python", "import requests");
                log.info("✓ requests 包导入成功");
                
                // 获取 requests 版本
                Value version = context.eval("python", "requests.__version__");
                String requestsVersion = version.asString();
                log.info("requests 版本: {}", requestsVersion);
                assertNotNull("requests 版本不应为空", requestsVersion);
                
                // 测试 requests 相关依赖
                context.eval("python", "import urllib3");
                context.eval("python", "import certifi");
                context.eval("python", "import charset_normalizer");
                context.eval("python", "import idna");
                
                log.info("✓ requests 相关依赖导入成功");
                
            } catch (Exception importError) {
                log.error("requests 导入异常详情:", importError);
                
                // 尝试列出可用的模块
                try {
                    Value availableModules = context.eval("python", """
                        import pkgutil
                        [name for importer, name, ispkg in pkgutil.iter_modules()][:20]
                        """);
                    log.info("可用模块（前20个）: {}", availableModules);
                } catch (Exception e) {
                    log.error("无法列出可用模块", e);
                }
                
                throw importError;
            }
            
        } catch (Exception e) {
            log.error("requests 包测试失败", e);
            if (e.getCause() != null) {
                log.error("原因:", e.getCause());
            }
            fail("requests 导入失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
        }
    }
    
    @Test
    public void testRequestsBasicFunctionality() {
        log.info("==== 测试 requests 基本功能 ====");
        
        try (Context context = GraalPyResources.contextBuilder()
                .allowIO(IOAccess.ALL)
                .allowNativeAccess(true)
                .allowHostAccess(HostAccess.ALL)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            
            // 测试 requests 基本 API
            String pythonCode = """
                import requests
                
                # 测试 Session 创建
                session = requests.Session()
                
                # 测试基本 API 存在性
                assert hasattr(requests, 'get')
                assert hasattr(requests, 'post')
                assert hasattr(requests, 'put')
                assert hasattr(requests, 'delete')
                
                # 测试 Response 类
                assert hasattr(requests, 'Response')
                
                result = "requests API 检查通过"
                """;
            
            context.eval("python", pythonCode);
            Value result = context.eval("python", "result");
            assertEquals("requests API 检查通过", result.asString());
            
            log.info("✓ requests 基本 API 功能正常");
            
        } catch (Exception e) {
            log.error("requests 基本功能测试失败", e);
            fail("requests 基本功能测试失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testPyContextPoolIntegration() {
        log.info("==== 测试 PyContextPool 集成 ====");
        
        PyContextPool pool = PyContextPool.getInstance();
        
        try (Context context = pool.createFreshContext()) {
            log.info("✓ PyContextPool.createFreshContext() 成功");
            
            // 测试 requests 导入
            context.eval("python", "import requests");
            log.info("✓ 通过 PyContextPool 创建的 Context 可以导入 requests");
            
            // 注入测试对象
            Value bindings = context.getBindings("python");
            bindings.putMember("test_message", "Hello from Java");
            
            Value result = context.eval("python", "test_message + ' to Python'");
            assertEquals("Hello from Java to Python", result.asString());
            
            log.info("✓ Java 对象注入正常");
            
        } catch (Exception e) {
            log.error("PyContextPool 集成测试失败", e);
            fail("PyContextPool 集成测试失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testComplexPythonScript() {
        log.info("==== 测试复杂 Python 脚本 ====");
        
        try (Context context = GraalPyResources.contextBuilder()
                .allowIO(IOAccess.ALL)
                .allowNativeAccess(true)
                .allowHostAccess(HostAccess.ALL)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            
            String complexScript = """
                import requests
                import json
                import re
                import sys
                import time
                import random
                
                def test_function():
                    # 测试各种 Python 功能
                    data = {
                        'requests_version': requests.__version__,
                        'python_version': sys.version,
                        'random_number': random.randint(1, 100),
                        'current_time': time.time()
                    }
                    
                    # 测试 JSON 序列化
                    json_str = json.dumps(data)
                    parsed_data = json.loads(json_str)
                    
                    # 测试正则表达式
                    version_match = re.search(r'(\\d+\\.\\d+\\.\\d+)', parsed_data['requests_version'])
                    
                    return {
                        'success': True,
                        'requests_version': parsed_data['requests_version'],
                        'version_match': version_match is not None,
                        'data_count': len(parsed_data)
                    }
                
                # 执行测试
                result = test_function()
                """;
            
            context.eval("python", complexScript);
            Value result = context.eval("python", "result");
            
            assertTrue("脚本执行应该成功", result.getMember("success").asBoolean());
            assertNotNull("requests 版本应该存在", result.getMember("requests_version").asString());
            assertTrue("版本匹配应该成功", result.getMember("version_match").asBoolean());
            assertEquals("数据项数量应该为4", 4, result.getMember("data_count").asInt());
            
            log.info("✓ 复杂 Python 脚本执行成功");
            log.info("requests 版本: {}", result.getMember("requests_version").asString());
            
        } catch (Exception e) {
            log.error("复杂 Python 脚本测试失败", e);
            fail("复杂脚本执行失败: " + e.getMessage());
        }
    }
}