package cn.qaiu.parser.custompy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python 代码安全检查器
 * 在执行前对代码进行静态分析，检测危险操作
 */
public class PyCodeSecurityChecker {
    
    private static final Logger log = LoggerFactory.getLogger(PyCodeSecurityChecker.class);
    
    /**
     * 危险的导入模块
     */
    private static final Set<String> DANGEROUS_IMPORTS = Set.of(
            "subprocess",    // 子进程执行
            "socket",        // 原始网络套接字
            "ctypes",        // C 语言接口
            "_ctypes",       // C 语言接口
            "multiprocessing", // 多进程
            "threading",     // 多线程（可选禁止）
            "asyncio",       // 异步IO（可选禁止）
            "pty",           // 伪终端
            "fcntl",         // 文件控制
            "resource",      // 资源限制
            "syslog",        // 系统日志
            "signal"         // 信号处理
    );
    
    /**
     * 危险的 os 模块方法
     */
    private static final Set<String> DANGEROUS_OS_METHODS = Set.of(
            "system",        // 执行系统命令
            "popen",         // 打开进程管道
            "spawn",         // 生成进程
            "spawnl", "spawnle", "spawnlp", "spawnlpe",
            "spawnv", "spawnve", "spawnvp", "spawnvpe",
            "exec", "execl", "execle", "execlp", "execlpe",
            "execv", "execve", "execvp", "execvpe",
            "fork", "forkpty",
            "kill", "killpg",
            "remove", "unlink",
            "rmdir", "removedirs",
            "mkdir", "makedirs",
            "rename", "renames", "replace",
            "chmod", "chown", "lchown",
            "chroot",
            "mknod", "mkfifo",
            "link", "symlink"
    );
    
    /**
     * 危险的内置函数
     */
    private static final Set<String> DANGEROUS_BUILTINS = Set.of(
            "exec",          // 执行代码
            "eval",          // 评估表达式
            "compile",       // 编译代码
            "__import__"     // 动态导入
    );
    
    /**
     * 检查代码安全性
     * @param code Python 代码
     * @return 安全检查结果
     */
    public static SecurityCheckResult check(String code) {
        if (code == null || code.trim().isEmpty()) {
            return SecurityCheckResult.fail("代码为空");
        }
        
        List<String> violations = new ArrayList<>();
        
        // 1. 检查危险导入
        for (String module : DANGEROUS_IMPORTS) {
            if (containsImport(code, module)) {
                violations.add("禁止导入危险模块: " + module);
            }
        }
        
        // 2. 检查危险的 os 方法调用
        for (String method : DANGEROUS_OS_METHODS) {
            if (containsOsMethodCall(code, method)) {
                violations.add("禁止使用危险的 os 方法: os." + method + "()");
            }
        }
        
        // 3. 检查危险的内置函数
        for (String builtin : DANGEROUS_BUILTINS) {
            if (containsBuiltinCall(code, builtin)) {
                violations.add("禁止使用危险的内置函数: " + builtin + "()");
            }
        }
        
        // 4. 检查危险的文件操作模式
        if (containsDangerousFileOperation(code)) {
            violations.add("禁止使用危险的文件写入操作");
        }
        
        if (violations.isEmpty()) {
            return SecurityCheckResult.pass();
        } else {
            return SecurityCheckResult.fail(String.join("; ", violations));
        }
    }
    
    /**
     * 检查是否包含指定模块的导入
     */
    private static boolean containsImport(String code, String module) {
        // 匹配: import module / from module import xxx
        String pattern1 = "(?m)^\\s*import\\s+" + Pattern.quote(module) + "\\b";
        String pattern2 = "(?m)^\\s*from\\s+" + Pattern.quote(module) + "\\s+import";
        
        return Pattern.compile(pattern1).matcher(code).find() ||
               Pattern.compile(pattern2).matcher(code).find();
    }
    
    /**
     * 检查是否包含指定的 os 方法调用
     */
    private static boolean containsOsMethodCall(String code, String method) {
        // 匹配: os.method(
        String pattern = "\\bos\\s*\\.\\s*" + Pattern.quote(method) + "\\s*\\(";
        return Pattern.compile(pattern).matcher(code).find();
    }
    
    /**
     * 检查是否包含指定的内置函数调用
     */
    private static boolean containsBuiltinCall(String code, String builtin) {
        // 匹配: builtin( 但排除方法调用 xxx.builtin(
        String pattern = "(?<!\\.)\\b" + Pattern.quote(builtin) + "\\s*\\(";
        return Pattern.compile(pattern).matcher(code).find();
    }
    
    /**
     * 检查是否包含危险的文件操作
     */
    private static boolean containsDangerousFileOperation(String code) {
        // 检查 open() 的写入模式
        Pattern openPattern = Pattern.compile("\\bopen\\s*\\([^)]*['\"][wax+]['\"]");
        if (openPattern.matcher(code).find()) {
            return true;
        }
        
        // 检查直接的文件写入
        Pattern writePattern = Pattern.compile("\\.write\\s*\\(|\\.writelines\\s*\\(");
        if (writePattern.matcher(code).find()) {
            // 需要进一步判断是否是文件写入而不是 response 写入等
            // 这里简单处理，如果有 write 调用但没有 requests/http 相关的上下文，则禁止
            if (!code.contains("requests") && !code.contains("http")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 安全检查结果
     */
    public static class SecurityCheckResult {
        private final boolean passed;
        private final String message;
        
        private SecurityCheckResult(boolean passed, String message) {
            this.passed = passed;
            this.message = message;
        }
        
        public static SecurityCheckResult pass() {
            return new SecurityCheckResult(true, null);
        }
        
        public static SecurityCheckResult fail(String message) {
            return new SecurityCheckResult(false, message);
        }
        
        public boolean isPassed() {
            return passed;
        }
        
        public String getMessage() {
            return message;
        }
        
        @Override
        public String toString() {
            return passed ? "PASSED" : "FAILED: " + message;
        }
    }
}
