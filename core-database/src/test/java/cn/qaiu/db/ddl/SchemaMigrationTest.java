package cn.qaiu.db.ddl;

import cn.qaiu.db.pool.JDBCType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.templates.annotations.Column;
import lombok.Data;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * SchemaMigration 单元测试
 */
public class SchemaMigrationTest {

    private Vertx vertx;
    private JDBCPool pool;
//
//    @Before
//    public void setUp() {
//        vertx = Vertx.vertx();
//
//        // 创建 H2 内存数据库连接池
//        pool = JDBCPool.pool(vertx,
//            "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
//            "sa",
//            ""
//        );
//    }

    @After
    public void tearDown() {
        if (pool != null) {
            pool.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    /**
     * 测试添加新字段
     */
    @Test
    public void testAddNewField() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        // 1. 先创建一个基础表
        String createTableSQL = """
            CREATE TABLE test_user (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(50) NOT NULL
            )
            """;
        
        pool.query(createTableSQL).execute()
            .compose(v -> {
                // 2. 使用 SchemaMigration 添加新字段
                return SchemaMigration.migrateTable(pool, TestUserWithNewField.class, JDBCType.H2DB);
            })
            .compose(v -> {
                // 3. 验证新字段是否添加成功
                return pool.query("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                                "WHERE TABLE_NAME = 'TEST_USER' AND COLUMN_NAME = 'EMAIL'")
                          .execute();
            })
            .onSuccess(rows -> {
                assertEquals("应该找到新添加的 email 字段", 1, rows.size());
                latch.countDown();
            })
            .onFailure(err -> {
                fail("测试失败: " + err.getMessage());
                latch.countDown();
            });
        
        assertTrue("测试超时", latch.await(10, TimeUnit.SECONDS));
    }

    /**
     * 测试不添加已存在的字段
     */
    @Test
    public void testSkipExistingField() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        // 1. 创建包含 email 字段的表
        String createTableSQL = """
            CREATE TABLE test_user2 (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(50) NOT NULL,
                email VARCHAR(100)
            )
            """;
        
        pool.query(createTableSQL).execute()
            .compose(v -> {
                // 2. 尝试再次添加 email 字段（应该跳过）
                return SchemaMigration.migrateTable(pool, TestUserWithNewField2.class, JDBCType.H2DB);
            })
            .onSuccess(v -> {
                // 3. 验证表结构正常，没有错误
                latch.countDown();
            })
            .onFailure(err -> {
                fail("测试失败: " + err.getMessage());
                latch.countDown();
            });
        
        assertTrue("测试超时", latch.await(10, TimeUnit.SECONDS));
    }

    /**
     * 测试没有 @NewField 注解时不执行迁移
     */
    @Test
    public void testNoNewFieldAnnotation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        // 1. 创建基础表
        String createTableSQL = """
            CREATE TABLE test_user3 (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(50) NOT NULL
            )
            """;
        
        pool.query(createTableSQL).execute()
            .compose(v -> {
                // 2. 使用没有 @NewField 注解的实体类
                return SchemaMigration.migrateTable(pool, TestUserNoAnnotation.class, JDBCType.H2DB);
            })
            .compose(v -> {
                // 3. 验证没有添加 email 字段
                return pool.query("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                                "WHERE TABLE_NAME = 'TEST_USER3' AND COLUMN_NAME = 'EMAIL'")
                          .execute();
            })
            .onSuccess(rows -> {
                assertEquals("不应该添加没有 @NewField 注解的字段", 0, rows.size());
                latch.countDown();
            })
            .onFailure(err -> {
                fail("测试失败: " + err.getMessage());
                latch.countDown();
            });
        
        assertTrue("测试超时", latch.await(10, TimeUnit.SECONDS));
    }

    /**
     * 测试多个新字段同时添加
     */
    @Test
    public void testMultipleNewFields() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        // 1. 创建基础表
        String createTableSQL = """
            CREATE TABLE test_user4 (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(50) NOT NULL
            )
            """;
        
        pool.query(createTableSQL).execute()
            .compose(v -> {
                // 2. 添加多个新字段
                return SchemaMigration.migrateTable(pool, TestUserMultipleNewFields.class, JDBCType.H2DB);
            })
            .compose(v -> {
                // 3. 验证所有新字段都添加成功
                return pool.query("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                                "WHERE TABLE_NAME = 'TEST_USER4' AND COLUMN_NAME IN ('EMAIL', 'PHONE', 'ADDRESS')")
                          .execute();
            })
            .onSuccess(rows -> {
                int count = rows.iterator().next().getInteger(0);
                assertEquals("应该添加 3 个新字段", 3, count);
                latch.countDown();
            })
            .onFailure(err -> {
                fail("测试失败: " + err.getMessage());
                latch.countDown();
            });
        
        assertTrue("测试超时", latch.await(10, TimeUnit.SECONDS));
    }

    // ========== 测试实体类 ==========

    @Data
    @Table("test_user")
    static class TestUserWithNewField {
        @Constraint(autoIncrement = true)
        private Long id;
        
        @Length(varcharSize = 50)
        @Constraint(notNull = true)
        private String name;
        
        @NewField("用户邮箱")
        @Length(varcharSize = 100)
        private String email;
    }

    @Data
    @Table("test_user2")
    static class TestUserWithNewField2 {
        @Constraint(autoIncrement = true)
        private Long id;
        
        @Length(varcharSize = 50)
        @Constraint(notNull = true)
        private String name;
        
        @NewField("用户邮箱")
        @Length(varcharSize = 100)
        private String email;
    }

    @Data
    @Table("test_user3")
    static class TestUserNoAnnotation {
        @Constraint(autoIncrement = true)
        private Long id;
        
        @Length(varcharSize = 50)
        @Constraint(notNull = true)
        private String name;
        
        // 没有 @NewField 注解
        @Length(varcharSize = 100)
        private String email;
    }

    @Data
    @Table("test_user4")
    static class TestUserMultipleNewFields {
        @Constraint(autoIncrement = true)
        private Long id;
        
        @Length(varcharSize = 50)
        @Constraint(notNull = true)
        private String name;
        
        @NewField("用户邮箱")
        @Length(varcharSize = 100)
        private String email;
        
        @NewField("手机号")
        @Length(varcharSize = 20)
        private String phone;
        
        @NewField("地址")
        @Length(varcharSize = 255)
        private String address;
    }
}
