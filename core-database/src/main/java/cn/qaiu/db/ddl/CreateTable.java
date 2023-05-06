package cn.qaiu.db.ddl;

import cn.qaiu.vx.core.util.ReflectionUtil;
import io.vertx.codegen.format.CamelCase;
import io.vertx.codegen.format.Case;
import io.vertx.codegen.format.LowerCamelCase;
import io.vertx.codegen.format.SnakeCase;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.templates.annotations.Column;
import io.vertx.sqlclient.templates.annotations.RowMapped;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 创建表
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class CreateTable {
    public static Map<Class<?>, String> javaProperty2SqlColumnMap = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateTable.class);

    static {
        javaProperty2SqlColumnMap.put(Integer.class, "INT");
        javaProperty2SqlColumnMap.put(Short.class, "SMALLINT");
        javaProperty2SqlColumnMap.put(Byte.class, "TINYINT");
        javaProperty2SqlColumnMap.put(Long.class, "BIGINT");
        javaProperty2SqlColumnMap.put(java.math.BigDecimal.class, "DECIMAL");
        javaProperty2SqlColumnMap.put(Double.class, "DOUBLE");
        javaProperty2SqlColumnMap.put(Float.class, "REAL");
        javaProperty2SqlColumnMap.put(Boolean.class, "BOOLEAN");
        javaProperty2SqlColumnMap.put(String.class, "VARCHAR");
        javaProperty2SqlColumnMap.put(java.util.Date.class, "TIMESTAMP");
        javaProperty2SqlColumnMap.put(java.sql.Timestamp.class, "TIMESTAMP");
        javaProperty2SqlColumnMap.put(java.sql.Date.class, "DATE");
        javaProperty2SqlColumnMap.put(java.sql.Time.class, "TIME");

        javaProperty2SqlColumnMap.put(int.class, "INT");
        javaProperty2SqlColumnMap.put(short.class, "SMALLINT");
        javaProperty2SqlColumnMap.put(byte.class, "TINYINT");
        javaProperty2SqlColumnMap.put(long.class, "BIGINT");
        javaProperty2SqlColumnMap.put(double.class, "DOUBLE");
        javaProperty2SqlColumnMap.put(float.class, "REAL");
        javaProperty2SqlColumnMap.put(boolean.class, "BOOLEAN");
    }

    private static Case getCase(Class<?> clz) {
        switch (clz.getName()) {
            case "io.vertx.codegen.format.CamelCase":
                return CamelCase.INSTANCE;
            case "io.vertx.codegen.format.SnakeCase":
                return SnakeCase.INSTANCE;
            case "io.vertx.codegen.format.LowerCamelCase":
                return LowerCamelCase.INSTANCE;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public static String getCreateTableSQL(Class<?> clz) {
        // 判断类上是否有次注解
        String primaryKey = null; // 主键
        String tableName = null; // 表名
        Case caseFormat = SnakeCase.INSTANCE;
        if (clz.isAnnotationPresent(RowMapped.class)) {
            RowMapped annotation = clz.getAnnotation(RowMapped.class);
            Class<? extends Case> formatter = annotation.formatter();
            caseFormat = getCase(formatter);
        }

        if (clz.isAnnotationPresent(Table.class)) {
            // 获取类上的注解
            Table annotation = clz.getAnnotation(Table.class);
            // 输出注解上的类名
            String tableNameAnnotation = annotation.value();
            if (StringUtils.isNotEmpty(tableNameAnnotation)) {
                tableName = tableNameAnnotation;
            } else {
                tableName = LowerCamelCase.INSTANCE.to(caseFormat, clz.getSimpleName());
            }
            primaryKey = annotation.keyFields();
        }
        Field[] fields = clz.getDeclaredFields();
        String column;
        int[] decimalSize = {22, 2};
        int varcharSize = 255;
        StringBuilder sb = new StringBuilder(50);
        sb.append("CREATE TABLE IF NOT EXISTS \"").append(tableName).append("\" ( \r\n ");
        boolean firstId = true;
        for (Field f : fields) {
            Class<?> paramType = f.getType();
            String sqlType = javaProperty2SqlColumnMap.get(paramType);
            if (f.getName().equals("serialVersionUID") || StringUtils.isEmpty(sqlType) || f.isAnnotationPresent(TableGenIgnore.class)) {
                continue;
            }
            column = LowerCamelCase.INSTANCE.to(caseFormat, f.getName());
            if (f.isAnnotationPresent(Column.class)) {
                Column columnAnnotation = f.getAnnotation(Column.class);
                //输出注解属性
                if (StringUtils.isNotBlank(columnAnnotation.name())) {
                    column = columnAnnotation.name();
                }
            }
            if (f.isAnnotationPresent(Length.class)) {
                Length fieldAnnotation = f.getAnnotation(Length.class);
                decimalSize = fieldAnnotation.decimalSize();
                varcharSize = fieldAnnotation.varcharSize();
            }
            sb.append("\"").append(column).append("\"");
            sb.append(" ").append(sqlType);
            // 添加类型长度
            if (sqlType.equals("DECIMAL")) {
                sb.append("(").append(decimalSize[0]).append(",").append(decimalSize[1]).append(")");
            }
            if (sqlType.equals("VARCHAR")) {
                sb.append("(").append(varcharSize).append(")");
            }
            if (f.isAnnotationPresent(Constraint.class)) {
                Constraint constraintAnnotation = f.getAnnotation(Constraint.class);
                if (constraintAnnotation.notNull()) {
                    //非空约束
                    sb.append(" NOT NULL");
                }
                String apostrophe = constraintAnnotation.defaultValueIsFunction() ? "" : "'";
                if (StringUtils.isNotEmpty(constraintAnnotation.defaultValue())) {
                    //默认值约束
                    sb.append(" DEFAULT ").append(apostrophe).append(constraintAnnotation.defaultValue()).append(apostrophe);
                }
                if (constraintAnnotation.autoIncrement() && paramType.equals(Integer.class) || paramType.equals(Long.class)) {
                    ////自增
                    sb.append(" AUTO_INCREMENT");
                }
            }
            if (StringUtils.isEmpty(primaryKey)) {
                if (firstId) {//类型转换
                    sb.append(" PRIMARY KEY");
                    firstId = false;
                }
            } else {
                if (primaryKey.equals(column.toLowerCase())) {
                    sb.append(" PRIMARY KEY");
                }
            }
            sb.append(",\n ");
        }
        String sql = sb.toString();
        //去掉最后一个逗号
        int lastIndex = sql.lastIndexOf(",");
        sql = sql.substring(0, lastIndex) + sql.substring(lastIndex + 1);
        return sql.substring(0, sql.length() - 1) + ");\r\n";
    }

    public static void createTable(JDBCPool pool, String tableClassPath) {
        Set<Class<?>> tableClassList = ReflectionUtil.getReflections(tableClassPath).getTypesAnnotatedWith(Table.class);
        if (tableClassList.isEmpty()) LOGGER.info("Table model class not fount");
        tableClassList.forEach(clazz -> {
            String createTableSQL = getCreateTableSQL(clazz);
            pool.query(createTableSQL).execute().onSuccess(
                    rs -> LOGGER.info("\n" + createTableSQL + "create table --> ok")
            ).onFailure(Throwable::printStackTrace);
        });
    }
}
