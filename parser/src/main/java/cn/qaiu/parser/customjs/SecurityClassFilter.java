package cn.qaiu.parser.customjs;

import org.openjdk.nashorn.api.scripting.ClassFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaScript执行器安全类过滤器
 * 用于限制JavaScript代码可以访问的Java类，防止恶意代码执行危险操作
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class SecurityClassFilter implements ClassFilter {
    
    private static final Logger log = LoggerFactory.getLogger(SecurityClassFilter.class);
    
    // 危险类黑名单
    private static final String[] DANGEROUS_CLASSES = {
        // 系统命令执行
        "java.lang.Runtime",
        "java.lang.ProcessBuilder",
        "java.lang.Process",
        
        // 文件系统访问
        "java.io.File",
        "java.io.FileInputStream",
        "java.io.FileOutputStream",
        "java.io.FileReader",
        "java.io.FileWriter",
        "java.io.RandomAccessFile",
        "java.nio.file.Files",
        "java.nio.file.Paths",
        "java.nio.file.Path",
        "java.nio.channels.FileChannel",
        
        // 系统访问
        "java.lang.System",
        "java.lang.SecurityManager",
        
        // 反射相关
        "java.lang.Class",
        "java.lang.reflect.Method",
        "java.lang.reflect.Field",
        "java.lang.reflect.Constructor",
        "java.lang.reflect.AccessibleObject",
        "java.lang.ClassLoader",
        
        // 网络访问
        "java.net.Socket",
        "java.net.ServerSocket",
        "java.net.DatagramSocket",
        "java.net.URL",
        "java.net.URLConnection",
        "java.net.HttpURLConnection",
        "java.net.InetAddress",
        
        // 线程和并发
        "java.lang.Thread",
        "java.lang.ThreadGroup",
        "java.util.concurrent.Executor",
        "java.util.concurrent.ExecutorService",
        
        // 数据库访问
        "java.sql.Connection",
        "java.sql.Statement",
        "java.sql.PreparedStatement",
        "java.sql.DriverManager",
        
        // 脚本引擎（防止嵌套执行）
        "javax.script.ScriptEngine",
        "javax.script.ScriptEngineManager",
        
        // JVM控制
        "java.lang.invoke.MethodHandle",
        "sun.misc.Unsafe",
        
        // Nashorn内部类
        "jdk.nashorn.internal",
        "jdk.internal",
    };
    
    @Override
    public boolean exposeToScripts(String className) {
        // 检查是否在黑名单中
        for (String dangerous : DANGEROUS_CLASSES) {
            if (className.equals(dangerous) || className.startsWith(dangerous + ".")) {
                log.warn("🔒 安全拦截: JavaScript尝试访问危险类 - {}", className);
                return false;
            }
        }
        
        // 额外的包级别限制
        String[] dangerousPackages = {
            "java.lang.reflect.",
            "java.io.",
            "java.nio.",
            "java.net.",
            "java.sql.",
            "javax.script.",
            "sun.",
            "jdk.internal.",
            "jdk.nashorn.internal."
        };
        
        for (String pkg : dangerousPackages) {
            if (className.startsWith(pkg)) {
                log.warn("🔒 安全拦截: JavaScript尝试访问危险包 - {}", className);
                return false;
            }
        }
        
        // 默认也拒绝（白名单策略更安全，但这里为了兼容性使用黑名单）
        // 如果要更严格，可以改为 return false
        log.debug("允许访问类: {}", className);
        return true;
    }
}

