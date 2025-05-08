package cn.qaiu.db.ddl;

import cn.qaiu.db.pool.JDBCType;
import io.vertx.sqlclient.templates.annotations.Column;
import org.junit.Test;

public class CreateTableTest {



    static class TestModel2 {
        @Column(name = "id")
        @Constraint(autoIncrement = true)
        private Long id;

        @Column(name = "name")
        @Constraint(notNull = true, uniqueKey = "ne_unique")
        private String name;

        @Column(name = "age")
        private Integer age;

        @Column(name = "email")
        @Constraint(notNull = true, uniqueKey = "ne_unique")
        private String email;

        @Column(name = "created_at")
        private java.util.Date createdAt;
    }

    @Test
    public void getCreateTableSQL() {
        // 测试
        String sql = String.join("\n", CreateTable.getCreateTableSQL(TestModel2.class, JDBCType.H2DB));
        System.out.println(sql);
    }
    @Test
    public void getCreateTableSQL2() {
        // 测试
        String sql = String.join("\n", CreateTable.getCreateTableSQL(TestModel2.class, JDBCType.MySQL));
        System.out.println(sql);
    }
    @Test
    public void getCreateTableSQL3() {
        // 测试
        String sql = String.join("\n", CreateTable.getCreateTableSQL(TestModel2.class, JDBCType.PostgreSQL));
        System.out.println(sql);
    }
}
