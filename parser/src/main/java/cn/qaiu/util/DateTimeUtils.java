package cn.qaiu.util;

import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具类，用于转换各种时间戳格式为可读的日期字符串
 */
public class DateTimeUtils {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * 将毫秒时间戳转换为 yyyy-MM-dd HH:mm:ss 格式
     * @param millis 毫秒时间戳
     * @return 格式化后的日期字符串
     */
    public static String formatMillisToDateTime(long millis) {
        return FORMATTER.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    /**
     * 将毫秒时间戳字符串转换为 yyyy-MM-dd HH:mm:ss 格式
     * @param millisStr 毫秒时间戳字符串（如 "1684737715067"）
     * @return 格式化后的日期字符串
     */
    public static String formatMillisStringToDateTime(String millisStr) {
        if (millisStr == null || millisStr.trim().isEmpty()) {
            return "";
        }
        try {
            long millis = Long.parseLong(millisStr.trim());
            return formatMillisToDateTime(millis);
        } catch (NumberFormatException e) {
            // 如果解析失败，返回原始值
            return millisStr;
        }
    }

    /**
     * 将秒级时间戳转换为 yyyy-MM-dd HH:mm:ss 格式
     * @param seconds 秒级时间戳
     * @return 格式化后的日期字符串
     */
    public static String formatSecondsToDateTime(long seconds) {
        return FORMATTER.format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    /**
     * 将秒级时间戳字符串转换为 yyyy-MM-dd HH:mm:ss 格式
     * @param secondsStr 秒级时间戳字符串
     * @return 格式化后的日期字符串
     */
    public static String formatSecondsStringToDateTime(String secondsStr) {
        if (secondsStr == null || secondsStr.trim().isEmpty()) {
            return "";
        }
        try {
            long seconds = Long.parseLong(secondsStr.trim());
            return formatSecondsToDateTime(seconds);
        } catch (NumberFormatException e) {
            // 如果解析失败，返回原始值
            return secondsStr;
        }
    }

    /**
     * 智能转换时间戳：自动判断是毫秒还是秒级
     * 根据值的大小判断：如果大于等于 10000000000（即 2286-11-20），视为毫秒；否则视为秒级
     * @param timestamp 时间戳字符串
     * @return 格式化后的日期字符串
     */
    public static String formatTimestampToDateTime(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) {
            return "";
        }
        try {
            long value = Long.parseLong(timestamp.trim());
            // 10000000000 对应 2286-11-20（毫秒）或 1970-04-26（秒级）
            // 使用 10^10 作为分界线
            if (value >= 10_000_000_000L) {
                return formatMillisToDateTime(value);
            } else {
                return formatSecondsToDateTime(value);
            }
        } catch (NumberFormatException e) {
            // 如果是 ISO 8601 格式，尝试解析
            return formatISODateTime(timestamp);
        }
    }

    /**
     * 解析并格式化 ISO 8601 格式的日期时间字符串
     * @param isoDateTime ISO 8601 格式的日期时间字符串
     * @return 格式化后的日期字符串
     */
    public static String formatISODateTime(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.trim().isEmpty()) {
            return "";
        }
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(isoDateTime, ISO_FORMATTER);
            return FORMATTER.format(offsetDateTime.toLocalDateTime());
        } catch (Exception e) {
            // 如果格式化失败，直接返回原始值
            return isoDateTime;
        }
    }
}
