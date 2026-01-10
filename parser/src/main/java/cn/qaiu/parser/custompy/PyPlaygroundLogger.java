package cn.qaiu.parser.custompy;

import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Python演练场日志封装
 * 收集日志信息用于前端显示
 *
 * @author QAIU
 */
public class PyPlaygroundLogger extends PyLogger {
    
    private static final Logger log = LoggerFactory.getLogger(PyPlaygroundLogger.class);
    
    private final List<LogEntry> logs = new ArrayList<>();
    
    public PyPlaygroundLogger() {
        super("PyPlayground");
    }
    
    @Override
    @HostAccess.Export
    public void debug(String message) {
        super.debug(message);
        addLog("DEBUG", message);
    }
    
    @Override
    @HostAccess.Export
    public void debug(String message, Object... args) {
        super.debug(message, args);
        addLog("DEBUG", formatMessage(message, args));
    }
    
    @Override
    @HostAccess.Export
    public void info(String message) {
        super.info(message);
        addLog("INFO", message);
    }
    
    @Override
    @HostAccess.Export
    public void info(String message, Object... args) {
        super.info(message, args);
        addLog("INFO", formatMessage(message, args));
    }
    
    @Override
    @HostAccess.Export
    public void warn(String message) {
        super.warn(message);
        addLog("WARN", message);
    }
    
    @Override
    @HostAccess.Export
    public void warn(String message, Object... args) {
        super.warn(message, args);
        addLog("WARN", formatMessage(message, args));
    }
    
    @Override
    @HostAccess.Export
    public void error(String message) {
        super.error(message);
        addLog("ERROR", message);
    }
    
    @Override
    @HostAccess.Export
    public void error(String message, Object... args) {
        super.error(message, args);
        addLog("ERROR", formatMessage(message, args));
    }
    
    @Override
    @HostAccess.Export
    public void error(String message, Throwable throwable) {
        super.error(message, throwable);
        addLog("ERROR", message + " - " + throwable.getMessage());
    }
    
    /**
     * 添加Java内部日志（不在Python脚本中调用）
     */
    public void infoJava(String message) {
        log.info("[PyPlayground] " + message);
        addLog("INFO", "[Java] " + message, "java");
    }
    
    public void debugJava(String message) {
        log.debug("[PyPlayground] " + message);
        addLog("DEBUG", "[Java] " + message, "java");
    }
    
    public void errorJava(String message) {
        log.error("[PyPlayground] " + message);
        addLog("ERROR", "[Java] " + message, "java");
    }
    
    public void errorJava(String message, Throwable throwable) {
        log.error("[PyPlayground] " + message, throwable);
        addLog("ERROR", "[Java] " + message + " - " + throwable.getMessage(), "java");
    }
    
    private void addLog(String level, String message) {
        addLog(level, message, "python");
    }
    
    private void addLog(String level, String message, String source) {
        logs.add(new LogEntry(level, message, System.currentTimeMillis(), source));
    }
    
    private String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        
        // 简单的占位符替换
        String result = message;
        for (Object arg : args) {
            int index = result.indexOf("{}");
            if (index >= 0) {
                result = result.substring(0, index) + (arg != null ? arg.toString() : "null") + result.substring(index + 2);
            }
        }
        return result;
    }
    
    /**
     * 获取所有日志
     */
    public List<LogEntry> getLogs() {
        return new ArrayList<>(logs);
    }
    
    /**
     * 清空日志
     */
    public void clearLogs() {
        logs.clear();
    }
    
    /**
     * 获取日志数量
     */
    public int size() {
        return logs.size();
    }
    
    /**
     * 日志条目
     */
    public static class LogEntry {
        private final String level;
        private final String message;
        private final long timestamp;
        private final String source;
        
        public LogEntry(String level, String message, long timestamp) {
            this(level, message, timestamp, "python");
        }
        
        public LogEntry(String level, String message, long timestamp, String source) {
            this.level = level;
            this.message = message;
            this.timestamp = timestamp;
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
}
