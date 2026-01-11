package cn.qaiu.parser.custompy;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import static org.junit.Assert.*;

/**
 * 简化的 requests 测试
 */
public class SimpleRequestsTest {
    
    private static final Logger log = LoggerFactory.getLogger(SimpleRequestsTest.class);
    
    @Test
    public void testRequestsImportOnly() {
        log.info("==== 简单测试：只测试 requests 导入 ====");
        
        PyContextPool pool = PyContextPool.getInstance();
        
        try (Context context = pool.createFreshContext()) {
            log.info("Context 创建成功");
            
            // 只测试 requests 导入
            context.eval("python", "import requests");
            log.info("✓ requests 导入成功");
            
            // 获取版本
            Value version = context.eval("python", "requests.__version__");
            String versionStr = version.asString();
            log.info("requests 版本: {}", versionStr);
            
            assertNotNull("版本不应为空", versionStr);
            assertTrue("版本不应为空字符串", !versionStr.trim().isEmpty());
            
            // 测试基本属性存在
            Value hasGet = context.eval("python", "hasattr(requests, 'get')");
            assertTrue("应该有 get 方法", hasGet.asBoolean());
            
            log.info("✓ 所有测试通过");
            
        } catch (Exception e) {
            log.error("测试失败", e);
            fail("测试失败: " + e.getMessage());
        }
    }
}