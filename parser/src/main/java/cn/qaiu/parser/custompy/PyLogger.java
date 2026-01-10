package cn.qaiu.parser.custompy;

import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Python日志封装
 * 为Python脚本提供日志功能
 *
 * @author QAIU
 */
public class PyLogger {
    
    private final Logger logger;
    private final String prefix;
    
    public PyLogger(String name) {
        this.logger = LoggerFactory.getLogger(name);
        this.prefix = "[" + name + "] ";
    }
    
    public PyLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.prefix = "[" + clazz.getSimpleName() + "] ";
    }
    
    /**
     * 调试日志
     * @param message 日志消息
     */
    @HostAccess.Export
    public void debug(String message) {
        logger.debug(prefix + message);
    }
    
    /**
     * 调试日志（带参数）
     * @param message 日志消息模板
     * @param args 参数
     */
    @HostAccess.Export
    public void debug(String message, Object... args) {
        logger.debug(prefix + message, args);
    }
    
    /**
     * 信息日志
     * @param message 日志消息
     */
    @HostAccess.Export
    public void info(String message) {
        logger.info(prefix + message);
    }
    
    /**
     * 信息日志（带参数）
     * @param message 日志消息模板
     * @param args 参数
     */
    @HostAccess.Export
    public void info(String message, Object... args) {
        logger.info(prefix + message, args);
    }
    
    /**
     * 警告日志
     * @param message 日志消息
     */
    @HostAccess.Export
    public void warn(String message) {
        logger.warn(prefix + message);
    }
    
    /**
     * 警告日志（带参数）
     * @param message 日志消息模板
     * @param args 参数
     */
    @HostAccess.Export
    public void warn(String message, Object... args) {
        logger.warn(prefix + message, args);
    }
    
    /**
     * 错误日志
     * @param message 日志消息
     */
    @HostAccess.Export
    public void error(String message) {
        logger.error(prefix + message);
    }
    
    /**
     * 错误日志（带参数）
     * @param message 日志消息模板
     * @param args 参数
     */
    @HostAccess.Export
    public void error(String message, Object... args) {
        logger.error(prefix + message, args);
    }
    
    /**
     * 错误日志（带异常）
     * @param message 日志消息
     * @param throwable 异常对象
     */
    @HostAccess.Export
    public void error(String message, Throwable throwable) {
        logger.error(prefix + message, throwable);
    }
    
    /**
     * 检查是否启用调试级别日志
     * @return true表示启用，false表示不启用
     */
    @HostAccess.Export
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }
    
    /**
     * 检查是否启用信息级别日志
     * @return true表示启用，false表示不启用
     */
    @HostAccess.Export
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }
    
    /**
     * 获取原始Logger对象
     * @return Logger对象
     */
    public Logger getOriginalLogger() {
        return logger;
    }
}
