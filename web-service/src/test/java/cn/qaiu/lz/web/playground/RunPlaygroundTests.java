package cn.qaiu.lz.web.playground;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 手动运行 Playground 测试
 * 绕过 maven surefire 的 skipTests 配置
 */
public class RunPlaygroundTests {
    
    private static final Logger log = LoggerFactory.getLogger(RunPlaygroundTests.class);
    
    public static void main(String[] args) {
        log.info("======================================");
        log.info("     Python Playground 测试套件");
        log.info("======================================");
        
        // 运行 PyPlaygroundTest
        log.info("\n>>> 运行 PyPlaygroundTest...\n");
        Result result = JUnitCore.runClasses(PyPlaygroundTest.class);
        
        // 输出结果
        log.info("\n======================================");
        log.info("             测试结果");
        log.info("======================================");
        log.info("运行测试数: {}", result.getRunCount());
        log.info("失败测试数: {}", result.getFailureCount());
        log.info("忽略测试数: {}", result.getIgnoreCount());
        log.info("运行时间: {} ms", result.getRunTime());
        
        if (result.wasSuccessful()) {
            log.info("\n✅ 所有测试通过!");
        } else {
            log.error("\n❌ 部分测试失败:");
            for (Failure failure : result.getFailures()) {
                log.error("  - {}: {}", failure.getTestHeader(), failure.getMessage());
                if (failure.getTrace() != null) {
                    log.error("    堆栈: {}", failure.getTrace().substring(0, Math.min(500, failure.getTrace().length())));
                }
            }
        }
        
        // 退出码
        System.exit(result.wasSuccessful() ? 0 : 1);
    }
}
