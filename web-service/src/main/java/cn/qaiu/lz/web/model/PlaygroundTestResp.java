package cn.qaiu.lz.web.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 演练场测试响应模型
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Data
@Builder
public class PlaygroundTestResp {
    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 执行结果（根据方法类型返回不同格式）
     * - parse: String (下载链接)
     * - parseFileList: List<FileInfo>
     * - parseById: String (下载链接)
     */
    private Object result;

    /**
     * 执行日志列表
     */
    private List<LogEntry> logs;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 错误堆栈
     */
    private String stackTrace;

    /**
     * 执行时间（毫秒）
     */
    private long executionTime;

    /**
     * 日志条目
     */
    @Data
    @Builder
    public static class LogEntry {
        /**
         * 日志级别：DEBUG, INFO, WARN, ERROR
         */
        private String level;

        /**
         * 日志消息
         */
        private String message;

        /**
         * 日志时间戳
         */
        private long timestamp;

        /**
         * 日志来源：JS（JavaScript日志）或 JAVA（Java日志）
         */
        private String source;
    }
}

