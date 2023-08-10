package cn.qaiu.db.pool;

import cn.qaiu.db.ddl.CreateTable;
import cn.qaiu.db.server.H2ServerHolder;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

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

    private static JDBCPoolInit instance;

    public JDBCPoolInit(Builder builder) {
        this.dbConfig = builder.dbConfig;
        this.url = builder.url;
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
    public void initPool() {
        if (pool != null) {
            LOGGER.error("pool 重复初始化");
            return;
        }

        // 异步启动H2服务
        vertx.createSharedWorkerExecutor("h2-server", 1, Long.MAX_VALUE)
                .executeBlocking(this::h2serverExecute)
                .onSuccess(res->{
                    LOGGER.info(res);
                    // 初始化数据库连接
                    vertx.createSharedWorkerExecutor("sql-pool-init")
                            .executeBlocking(this::poolInitExecute)
                            .onSuccess(LOGGER::info)
                            .onFailure(Throwable::printStackTrace);
                })
                .onFailure(Throwable::printStackTrace);


    }

    private void poolInitExecute(Promise<String> promise) {
        // 初始化连接池
        pool = JDBCPool.pool(vertx, dbConfig);
        CreateTable.createTable(pool);
        promise.complete("init jdbc pool success");

    }

    private void checkOrCreateDBFile() {
        LOGGER.info("init sql start");
        String[] path = url.split("\\./");
        path[1] = path[1].split(";")[0];
        path[1] += ".mv.db";
        File file = new File(path[1]);
        if (!file.exists()) {
            if (!file.getParentFile().exists()) {
                if (file.getParentFile().mkdirs()) {
                    LOGGER.info("mkdirs -> {}", file.getParentFile().getAbsolutePath());
                }
            }
            try {
                if (file.createNewFile()) {
                    LOGGER.info("create file -> {}", file.getAbsolutePath());
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
                throw new RuntimeException("file create failed");
            }
        }
    }

    private void h2serverExecute(Promise<String> promise) {
        // 初始化H2db, 创建本地db文件
        checkOrCreateDBFile();

        try {
            String url = dbConfig.getString("jdbcUrl");
            String[] portStr = url.split(":");
            String port = portStr[portStr.length - 1].split("[/\\\\]")[0];
            LOGGER.info("H2server listen port to {}", port);
            H2ServerHolder.init(Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", port).start());
            promise.complete("Start h2Server success");
        } catch (SQLException e) {
            throw new RuntimeException("Start h2Server failed: " + e.getMessage());
        }
    }

    /**
     * 获取连接池
     *
     * @return pool
     */
    public JDBCPool getPool() {
        return pool;
    }
}
