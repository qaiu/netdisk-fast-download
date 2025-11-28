package cn.qaiu.parser.customjs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 演练场日志收集器
 * 收集JavaScript执行过程中的日志信息
 * 注意：为避免Nashorn对Java重载方法的选择问题，所有日志方法都使用Object参数
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class JsPlaygroundLogger {
    
    // 使用线程安全的列表
    private final List<LogEntry> logs = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * 日志条目
     */
    public static class LogEntry {
        private final String level;
        private final String message;
        private final long timestamp;
        private final String source;  // "JS" 或 "JAVA"
        
        public LogEntry(String level, String message, String source) {
            this.level = level;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.source = source;
        }
        
        public String getLevel() {
            return level;
        }
        
        public String getMessage() {
            return message;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public String getSource() {
            return source;
        }
    }
    
    /**
     * 将任意对象转为字符串
     */
    private String toString(Object obj) {
        if (obj == null) {
            return "null";
        }
        return obj.toString();
    }
    
    /**
     * 记录日志（内部方法）
     * @param level 日志级别
     * @param message 日志消息
     * @param source 日志来源："JS" 或 "JAVA"
     */
    private void log(String level, Object message, String source) {
        String msg = toString(message);
        logs.add(new LogEntry(level, msg, source));
        System.out.println("[" + source + "PlaygroundLogger] " + level + ": " + msg);
    }
    
    /**
     * 调试日志（供JavaScript调用）
     * 使用Object参数避免Nashorn重载选择问题
     */
    public void debug(Object message) {
        log("DEBUG", message, "JS");
    }
    
    /**
     * 信息日志（供JavaScript调用）
     * 使用Object参数避免Nashorn重载选择问题
     */
    public void info(Object message) {
        log("INFO", message, "JS");
    }
    
    /**
     * 警告日志（供JavaScript调用）
     * 使用Object参数避免Nashorn重载选择问题
     */
    public void warn(Object message) {
        log("WARN", message, "JS");
    }
    
    /**
     * 错误日志（供JavaScript调用）
     * 使用Object参数避免Nashorn重载选择问题
     */
    public void error(Object message) {
        log("ERROR", message, "JS");
    }
    
    /**
     * 错误日志（带异常，供JavaScript调用）
     */
    public void error(Object message, Throwable throwable) {
        String msg = toString(message);
        if (throwable != null) {
            msg = msg + ": " + throwable.getMessage();
        }
        logs.add(new LogEntry("ERROR", msg, "JS"));
        System.out.println("[JSPlaygroundLogger] ERROR: " + msg);
    }
    
    // ===== 以下是供Java层调用的内部方法 =====
    
    /**
     * 调试日志（供Java层调用）
     */
    public void debugJava(String message) {
        log("DEBUG", message, "JAVA");
    }
    
    /**
     * 信息日志（供Java层调用）
     */
    public void infoJava(String message) {
        log("INFO", message, "JAVA");
    }
    
    /**
     * 警告日志（供Java层调用）
     */
    public void warnJava(String message) {
        log("WARN", message, "JAVA");
    }
    
    /**
     * 错误日志（供Java层调用）
     */
    public void errorJava(String message) {
        log("ERROR", message, "JAVA");
    }
    
    /**
     * 错误日志（带异常，供Java层调用）
     */
    public void errorJava(String message, Throwable throwable) {
        String msg = message;
        if (throwable != null) {
            msg = msg + ": " + throwable.getMessage();
        }
        logs.add(new LogEntry("ERROR", msg, "JAVA"));
        System.out.println("[JAVAPlaygroundLogger] ERROR: " + msg);
    }
    
    /**
     * 获取所有日志
     */
    public List<LogEntry> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }
    
    /**
     * 获取日志数量
     */
    public int size() {
        return logs.size();
    }
    
    /**
     * 清空日志
     */
    public void clear() {
        logs.clear();
    }
}
