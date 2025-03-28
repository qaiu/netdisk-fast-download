package cn.qaiu.db.pool;

import cn.qaiu.db.ddl.CreateTable;
import cn.qaiu.db.ddl.CreateDatabase;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 初始化JDBC
 * <br>Create date 2021/8/10 12:04
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class JDBCPoolInit {

    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCPoolInit.class);

    private static final String providerClass = io.vertx.ext.jdbc.spi.impl.HikariCPDataSourceProvider.class.getName();

    private JDBCPool pool = null;
    JsonObject dbConfig;
    Vertx vertx = VertxHolder.getVertxInstance();
    String url;

    private final JDBCType type;

    private static JDBCPoolInit instance;

    public JDBCType getType() {
        return type;
    }

    public JDBCPoolInit(Builder builder) {
        this.dbConfig = builder.dbConfig;
        this.url = builder.url;
        this.type = JDBCType.getJDBCTypeByURL(builder.url);
        if (StringUtils.isBlank(builder.dbConfig.getString("provider_class"))) {
            builder.dbConfig.put("provider_class", providerClass);
        }
        if (StringUtils.isBlank(builder.dbConfig.getString("driverClassName"))) {
            builder.dbConfig.put("driverClassName", this.type.getDriverClassName());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static JDBCPoolInit instance() {
        return instance;
    }

    public static class Builder {
        private JsonObject dbConfig;
        private String url;

        public Builder config(JsonObject dbConfig) {
            this.dbConfig = dbConfig;
            this.url = dbConfig.getString("jdbcUrl");
            return this;
        }

        public JDBCPoolInit build() {
            if (JDBCPoolInit.instance == null) {
                JDBCPoolInit.instance = new JDBCPoolInit(this);
            }
            return JDBCPoolInit.instance;
        }
    }

    /**
     * init h2db<br>
     * 这个方法只允许调用一次
     */
    synchronized public Future<Void> initPool() {
        if (pool != null) {
            LOGGER.error("pool 重复初始化");
            return null;
        }

        // 初始化数据库连接
        // 初始化连接池
        if (type == JDBCType.MySQL) {
            CreateDatabase.createDatabase(dbConfig);
        }
        pool = JDBCPool.pool(vertx, dbConfig);
        LOGGER.info("数据库连接初始化: URL=" + url);
        return CreateTable.createTable(pool, type);
    }

    /**
     * 获取连接池
     *
     * @return pool
     */
    synchronized public JDBCPool getPool() {
        return pool;
    }
}
