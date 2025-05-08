package cn.qaiu.db.pool;

import org.apache.commons.lang3.StringUtils;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * @since 2023/10/10 14:06
 */
public enum JDBCType {
    // 添加驱动类型字段
    MySQL("jdbc:mysql:"),
    H2DB("jdbc:h2:"),
    PostgreSQL("jdbc:postgresql:");
    private final String urlPrefix; // JDBC URL 前缀

    // 构造函数
    JDBCType(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    // 获取 JDBC URL 前缀
    public String getUrlPrefix() {
        return urlPrefix;
    }

    // 根据 JDBC URL 获取 JDBC 类型
    public static JDBCType getJDBCTypeByURL(String jdbcURL) {
        for (JDBCType jdbcType : values()) {
            if (StringUtils.startsWithIgnoreCase(jdbcURL, jdbcType.getUrlPrefix())) {
                return jdbcType;
            }
        }
        throw new RuntimeException("不支持的SQL类型: " + jdbcURL);
    }
}
