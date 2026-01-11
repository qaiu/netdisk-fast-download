package cn.qaiu.parser.custompy;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Python 代码安全检查器测试
 */
public class PyCodeSecurityCheckerTest {
    
    @Test
    public void testSafeCode() {
        String code = """
            import requests
            import json
            import re
            
            def parse(share_info, http, logger):
                response = requests.get(share_info.shareUrl)
                return response.text
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertTrue("安全代码应该通过检查", result.isPassed());
    }
    
    @Test
    public void testDangerousImport_subprocess() {
        String code = """
            import subprocess
            
            def parse(share_info, http, logger):
                result = subprocess.run(['ls', '-la'], capture_output=True)
                return result.stdout
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertFalse("导入 subprocess 应该被禁止", result.isPassed());
        assertTrue(result.getMessage().contains("subprocess"));
    }
    
    @Test
    public void testDangerousImport_socket() {
        String code = """
            import socket
            
            def parse(share_info, http, logger):
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                return "test"
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertFalse("导入 socket 应该被禁止", result.isPassed());
        assertTrue(result.getMessage().contains("socket"));
    }
    
    @Test
    public void testDangerousOsMethod_system() {
        String code = """
            import os
            
            def parse(share_info, http, logger):
                os.system('rm -rf /')
                return "test"
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertFalse("os.system 应该被禁止", result.isPassed());
        assertTrue(result.getMessage().contains("os.system"));
    }
    
    @Test
    public void testDangerousOsMethod_popen() {
        String code = """
            import os
            
            def parse(share_info, http, logger):
                result = os.popen('whoami').read()
                return result
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertFalse("os.popen 应该被禁止", result.isPassed());
        assertTrue(result.getMessage().contains("os.popen"));
    }
    
    @Test
    public void testDangerousBuiltin_exec() {
        String code = """
            def parse(share_info, http, logger):
                exec('print("hacked")')
                return "test"
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertFalse("exec() 应该被禁止", result.isPassed());
        assertTrue(result.getMessage().contains("exec"));
    }
    
    @Test
    public void testDangerousBuiltin_eval() {
        String code = """
            def parse(share_info, http, logger):
                result = eval('1+1')
                return str(result)
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertFalse("eval() 应该被禁止", result.isPassed());
        assertTrue(result.getMessage().contains("eval"));
    }
    
    @Test
    public void testSafeOsUsage_environ() {
        // os.environ 是安全的，应该允许
        String code = """
            import os
            
            def parse(share_info, http, logger):
                path = os.environ.get('PATH', '')
                return path
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertTrue("os.environ 应该是允许的", result.isPassed());
    }
    
    @Test
    public void testSafeOsUsage_path() {
        // os.path 是安全的
        String code = """
            import os
            
            def parse(share_info, http, logger):
                base = os.path.basename('/tmp/test.txt')
                return base
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertTrue("os.path 方法应该是允许的", result.isPassed());
    }
    
    @Test
    public void testDangerousFileWrite() {
        String code = """
            def parse(share_info, http, logger):
                with open('/tmp/hack.txt', 'w') as f:
                    f.write('hacked')
                return "test"
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertFalse("文件写入应该被禁止", result.isPassed());
        assertTrue(result.getMessage().contains("文件"));
    }
    
    @Test
    public void testSafeFileRead() {
        // 读取文件应该是允许的（实际上 GraalPy sandbox 会限制文件系统访问）
        String code = """
            def parse(share_info, http, logger):
                with open('/tmp/test.txt', 'r') as f:
                    content = f.read()
                return content
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        // 这里只做静态检查，读取模式 'r' 应该通过
        assertTrue("文件读取应该是允许的", result.isPassed());
    }
    
    @Test
    public void testEmptyCode() {
        var result = PyCodeSecurityChecker.check("");
        assertFalse("空代码应该失败", result.isPassed());
    }
    
    @Test
    public void testNullCode() {
        var result = PyCodeSecurityChecker.check(null);
        assertFalse("null 代码应该失败", result.isPassed());
    }
    
    @Test
    public void testMultipleViolations() {
        String code = """
            import subprocess
            import socket
            import os
            
            def parse(share_info, http, logger):
                os.system('ls')
                exec('print("hack")')
                return "test"
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertFalse("多个违规应该被检测到", result.isPassed());
        // 检查消息中包含多个违规项
        String message = result.getMessage();
        assertTrue(message.contains("subprocess"));
        assertTrue(message.contains("socket"));
        assertTrue(message.contains("os.system"));
        assertTrue(message.contains("exec"));
    }
    
    @Test
    public void testFromImport() {
        String code = """
            from subprocess import run
            
            def parse(share_info, http, logger):
                return "test"
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertFalse("from subprocess import 应该被禁止", result.isPassed());
    }
    
    @Test
    public void testRequestsWrite() {
        // 使用 requests 的 response 写入应该允许
        String code = """
            import requests
            
            def parse(share_info, http, logger):
                response = requests.get('http://example.com')
                # 这不是真正的文件写入
                return response.text
            """;
        
        var result = PyCodeSecurityChecker.check(code);
        assertTrue("requests 使用应该是允许的", result.isPassed());
    }
}
