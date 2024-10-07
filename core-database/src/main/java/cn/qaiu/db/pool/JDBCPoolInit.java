package cn.qaiu.db.pool;

import cn.qaiu.db.ddl.CreateTable;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 初始化JDBC
 * <br>Create date 2021/8/10 12:04
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class JDBCPoolInit {

    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCPoolInit.class);
    private JDBCPool pool = null;
    JsonObject dbConfig;
    Vertx vertx = VertxHolder.getVertxInstance();
    String url;
    private final JDBCType type;

    private static JDBCPoolInit instance;

    public JDBCPoolInit(Builder builder) {
        this.dbConfig = builder.dbConfig;
        this.url = builder.url;
        this.type = builder.type;
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
        private JDBCType type;

        public Builder config(JsonObject dbConfig) {
            this.dbConfig = dbConfig;
            this.url = dbConfig.getString("jdbcUrl");
            this.type = JDBCUtil.getJDBCType(dbConfig.getString("driverClassName"));
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
    synchronized public void initPool() {
        if (pool != null) {
            LOGGER.error("pool 重复初始化");
            return;
        }

        // 初始化数据库连接
        // 初始化连接池
        pool = JDBCPool.pool(vertx, dbConfig);
        CreateTable.createTable(pool, type);
        LOGGER.info("数据库连接初始化: URL=" + url);
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
