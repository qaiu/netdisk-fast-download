package cn.qaiu.db.ddl;

import cn.qaiu.db.pool.JDBCPoolInit;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CreateDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCPoolInit.class);

    /**
     * 解析数据库URL，获取数据库名
     * @param url 数据库URL
     * @return 数据库名
     */
    public static String getDatabaseName(String url) {
        // 正则表达式匹配数据库名
        String regex = "jdbc:mysql://[^/]+/(\\w+)(\\?.*)?";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new IllegalArgumentException("Invalid database URL: " + url);
        }
    }

    /**
     * 使用JDBC原生方法创建数据库
     * @param url 数据库连接URL
     * @param user 数据库用户名
     * @param password 数据库密码
     */
    public static void createDatabase(String url, String user, String password) {
        String dbName = getDatabaseName(url);
        LOGGER.info(">>>>>>>>>>> 创建数据库:'{}' <<<<<<<<<<<< ", dbName);

        // 去掉数据库名，构建不带数据库名的URL
        String baseUrl = url.substring(0, url.lastIndexOf("/") + 1) + "?characterEncoding=UTF-8&useUnicode=true";

        try (Connection conn = DriverManager.getConnection(baseUrl, user, password);
             Statement stmt = conn.createStatement()) {
            // 创建数据库
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            LOGGER.info(">>>>>>>>>>> 数据库'{}'创建成功 <<<<<<<<<<<<", dbName);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void createDatabase(JsonObject dbConfig) {
        createDatabase(
                dbConfig.getString("jdbcUrl"),
                dbConfig.getString("username"),
                dbConfig.getString("password")
        );
    }
}
