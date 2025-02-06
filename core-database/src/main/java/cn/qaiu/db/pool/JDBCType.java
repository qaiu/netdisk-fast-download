package cn.qaiu.db.pool;

import org.apache.commons.lang3.StringUtils;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * @since 2023/10/10 14:06
 */
public enum JDBCType {
    // 添加驱动类型字段
    MySQL("com.mysql.cj.jdbc.Driver", "jdbc:mysql:"),
    H2DB("org.h2.Driver", "jdbc:h2:");

    private final String driverClassName; // 驱动类名
    private final String urlPrefix; // JDBC URL 前缀

    // 构造函数
    JDBCType(String driverClassName, String urlPrefix) {
        this.driverClassName = driverClassName;
        this.urlPrefix = urlPrefix;
    }

    // 获取驱动类名
    public String getDriverClassName() {
        return driverClassName;
    }

    // 获取 JDBC URL 前缀
    public String getUrlPrefix() {
        return urlPrefix;
    }

    // 根据驱动类名获取 JDBC 类型
    public static JDBCType getJDBCType(String driverClassName) {
        for (JDBCType jdbcType : values()) {
            if (jdbcType.getDriverClassName().equalsIgnoreCase(driverClassName)) {
                return jdbcType;
            }
        }
        throw new RuntimeException("不支持的SQL驱动类型: " + driverClassName);
    }

    // 根据 JDBC URL 获取 JDBC 类型
    public static JDBCType getJDBCTypeByURL(String jdbcURL) {
        for (JDBCType jdbcType : values()) {
            if (StringUtils.startsWithIgnoreCase(jdbcURL, jdbcType.getUrlPrefix())) {
                return jdbcType;
            }
        }
        throw new RuntimeException("不支持的SQL驱动类型: " + jdbcURL);
    }
}
