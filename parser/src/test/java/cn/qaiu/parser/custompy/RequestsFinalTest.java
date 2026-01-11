package cn.qaiu.parser.custompy;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import static org.junit.Assert.*;

/**
 * 最终 requests 包测试
 * 验证修复后的 PyContextPool 是否能正确加载 requests
 */
public class RequestsFinalTest {
    
    private static final Logger log = LoggerFactory.getLogger(RequestsFinalTest.class);
    
    @Test
    public void testRequestsImportWithPyContextPool() {
        log.info("==== 最终测试：PyContextPool + requests 导入 ====");
        
        PyContextPool pool = PyContextPool.getInstance();
        
        try (Context context = pool.createFreshContext()) {
            log.info("Context 创建成功");
            
            // 测试 requests 导入
            context.eval("python", "import requests");
            log.info("✓ requests 导入成功");
            
            // 获取版本信息
            Value version = context.eval("python", "requests.__version__");
            String requestsVersion = version.asString();
            log.info("requests 版本: {}", requestsVersion);
            
            assertNotNull("requests 版本应该不为空", requestsVersion);
            assertFalse("requests 版本应该不为空字符串", requestsVersion.trim().isEmpty());
            
            // 测试相关依赖
            context.eval("python", "import urllib3");
            context.eval("python", "import certifi");
            context.eval("python", "import charset_normalizer");
            context.eval("python", "import idna");
            log.info("✓ requests 相关依赖导入成功");
            
            // 测试基本功能
            String testScript = """
                import requests
                
                # 测试 Session 创建
                session = requests.Session()
                
                # 测试基本 API 存在
                api_methods = ['get', 'post', 'put', 'delete', 'head', 'options']
                available_methods = [method for method in api_methods if hasattr(requests, method)]
                
                {
                    'version': requests.__version__,
                    'available_methods': available_methods,
                    'session_created': session is not None,
                    'test_success': True
                }
                """;
            
            Value result = context.eval("python", testScript);
            
            assertTrue("测试应该成功", result.getMember("test_success").asBoolean());
            assertTrue("Session应该创建成功", result.getMember("session_created").asBoolean());
            
            Value methods = result.getMember("available_methods");
            assertTrue("应该有可用的HTTP方法", methods.getArraySize() > 0);
            
            log.info("✓ requests 基本功能测试通过");
            log.info("可用方法: {}", methods);
            
        } catch (Exception e) {
            log.error("测试失败", e);
            fail("requests 导入或功能测试失败: " + e.getMessage());
        }
    }
    
    @Test
    public void testCompleteExample() {
        log.info("==== 测试完整的 Python 脚本示例 ====");
        
        PyContextPool pool = PyContextPool.getInstance();
        
        try (Context context = pool.createFreshContext()) {
            
            // 注入测试数据
            Value bindings = context.getBindings("python");
            bindings.putMember("test_url", "https://httpbin.org/json");
            
            String completeScript = """
                import requests
                import json
                import re
                import sys
                import time
                
                def test_complete_functionality():
                    # 模拟一个完整的 Python 脚本
                    result = {
                        'imports_success': True,
                        'requests_version': requests.__version__,
                        'python_version': sys.version_info[:2],
                        'timestamp': int(time.time()),
                        'json_test': json.dumps({'test': 'data'}),
                        'regex_test': bool(re.search(r'\\d+\\.\\d+', requests.__version__))
                    }
                    
                    # 测试 requests 基本结构
                    if hasattr(requests, 'get') and hasattr(requests, 'Session'):
                        result['requests_structure_ok'] = True
                    else:
                        result['requests_structure_ok'] = False
                    
                    return result
                
                # 执行测试
                test_result = test_complete_functionality()
                """;
            
            context.eval("python", completeScript);
            Value result = context.eval("python", "test_result");
            
            assertTrue("导入应该成功", result.getMember("imports_success").asBoolean());
            assertTrue("requests 结构应该正确", result.getMember("requests_structure_ok").asBoolean());
            assertTrue("正则匹配应该成功", result.getMember("regex_test").asBoolean());
            
            log.info("✓ 完整脚本测试成功");
            log.info("Python 版本: {}", result.getMember("python_version"));
            log.info("requests 版本: {}", result.getMember("requests_version"));
            
        } catch (Exception e) {
            log.error("完整脚本测试失败", e);
            fail("完整脚本测试失败: " + e.getMessage());
        }
    }
}