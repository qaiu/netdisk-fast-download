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

    // 白名单：明确允许 JS 解析器使用的类
    private static final String[] ALLOWED_CLASSES = {
        // Nashorn 脚本对象
        "org.openjdk.nashorn.api.scripting",
        "jdk.nashorn.api.scripting",
        // 基础集合类
        "java.util",
        // 基础类型
        "java.lang.String",
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Double",
        "java.lang.Boolean",
        "java.lang.Math",
        "java.lang.Number",
        "java.lang.Object",
        "java.lang.StringBuilder",
        "java.lang.StringBuffer",
        "java.lang.Character",
        "java.lang.Byte",
        "java.lang.Short",
        "java.lang.Float",
        "java.lang.Enum",
        "java.lang.Iterable",
        "java.lang.Comparable",
        // 时间类
        "java.time",
        // 文本处理
        "java.text",
    };

    // 白名单包前缀
    private static final String[] ALLOWED_PACKAGES = {
        "java.util.",
        "java.time.",
        "java.text.",
        "org.openjdk.nashorn.api.scripting.",
        "jdk.nashorn.api.scripting.",
    };

    @Override
    public boolean exposeToScripts(String className) {
        // 1. 先检查黑名单（快速拒绝已知危险类）
        for (String dangerous : DANGEROUS_CLASSES) {
            if (className.equals(dangerous) || className.startsWith(dangerous + ".")) {
                log.warn("🔒 安全拦截: JavaScript尝试访问危险类 - {}", className);
                return false;
            }
        }

        // 2. 检查白名单（只允许明确安全的类）
        for (String allowed : ALLOWED_CLASSES) {
            if (className.equals(allowed) || className.startsWith(allowed + ".")) {
                log.debug("✅ 白名单允许: {}", className);
                return true;
            }
        }

        // 3. 检查白名单包前缀
        for (String pkg : ALLOWED_PACKAGES) {
            if (className.startsWith(pkg)) {
                log.debug("✅ 白名单包允许: {}", className);
                return true;
            }
        }

        // 4. 默认拒绝（白名单策略）
        log.warn("🔒 安全拦截: JavaScript尝试访问未授权类 - {}", className);
        return false;
    }
}

