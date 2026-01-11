package cn.qaiu.db.ddl;

import cn.qaiu.db.pool.JDBCType;
import io.vertx.codegen.format.Case;
import io.vertx.codegen.format.LowerCamelCase;
import io.vertx.codegen.format.SnakeCase;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.templates.annotations.Column;
import io.vertx.sqlclient.templates.annotations.RowMapped;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 数据库表结构变更处理器
 * 用于在应用启动时自动检测并添加缺失的字段
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class SchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

    /**
     * 检查并迁移表结构
     * 只处理带有 @NewField 注解的字段，避免检查所有字段导致的重复错误
     *
     * @param pool 数据库连接池
     * @param clazz 实体类
     * @param type 数据库类型
     * @return Future
     */
    public static Future<Void> migrateTable(Pool pool, Class<?> clazz, JDBCType type) {
        Promise<Void> promise = Promise.promise();
        
        try {
            String tableName = getTableName(clazz);
            
            // 获取带有 @NewField 注解的字段
            List<Field> newFields = getNewFields(clazz);
            
            if (newFields.isEmpty()) {
                log.debug("表 '{}' 没有标记为 @NewField 的字段，跳过结构检查", tableName);
                promise.complete();
                return promise.future();
            }
            
            log.info("开始检查表 '{}' 的结构变更，新增字段数: {}", tableName, newFields.size());
            
            // 获取表的所有字段
            getTableColumns(pool, tableName, type)
                .compose(existingColumns -> {
                    // 只添加带有 @NewField 注解且不存在的字段
                    return addNewFields(pool, clazz, tableName, newFields, existingColumns, type);
                })
                .onSuccess(v -> {
                    log.info("表 '{}' 结构变更完成", tableName);
                    promise.complete();
                })
                .onFailure(err -> {
                    log.error("表 '{}' 结构变更失败", tableName, err);
                    promise.fail(err);
                });
                
        } catch (Exception e) {
            log.error("检查表结构失败", e);
            promise.fail(e);
        }
        
        return promise.future();
    }

    /**
     * 获取带有 @NewField 注解的字段列表
     */
    private static List<Field> getNewFields(Class<?> clazz) {
        List<Field> newFields = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(NewField.class) && !isIgnoredField(field)) {
                newFields.add(field);
                String desc = field.getAnnotation(NewField.class).value();
                if (StringUtils.isNotEmpty(desc)) {
                    log.debug("发现新字段: {} - {}", field.getName(), desc);
                } else {
                    log.debug("发现新字段: {}", field.getName());
                }
            }
        }
        return newFields;
    }

    /**
     * 获取表名
     */
    private static String getTableName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Table.class)) {
            Table annotation = clazz.getAnnotation(Table.class);
            if (StringUtils.isNotEmpty(annotation.value())) {
                return annotation.value();
            }
        }
        
        // 默认使用类名转下划线命名
        Case caseFormat = SnakeCase.INSTANCE;
        if (clazz.isAnnotationPresent(RowMapped.class)) {
            RowMapped annotation = clazz.getAnnotation(RowMapped.class);
            caseFormat = getCase(annotation.formatter());
        }
        return LowerCamelCase.INSTANCE.to(caseFormat, clazz.getSimpleName());
    }

    /**
     * 获取表的现有字段
     */
    private static Future<Set<String>> getTableColumns(Pool pool, String tableName, JDBCType type) {
        Promise<Set<String>> promise = Promise.promise();
        
        String sql = switch (type) {
            case MySQL -> String.format(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '%s'",
                tableName
            );
            case H2DB -> String.format(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = '%s'",
                tableName.toUpperCase()
            );
            case PostgreSQL -> String.format(
                "SELECT column_name FROM information_schema.columns WHERE table_name = '%s'",
                tableName.toLowerCase()
            );
        };
        
        pool.query(sql).execute()
            .onSuccess(rows -> {
                Set<String> columns = new HashSet<>();
                rows.forEach(row -> {
                    String columnName = row.getString(0);
                    if (columnName != null) {
                        columns.add(columnName.toLowerCase());
                    }
                });
                log.debug("表 '{}' 现有字段: {}", tableName, columns);
                promise.complete(columns);
            })
            .onFailure(err -> {
                log.warn("获取表 '{}' 字段列表失败，可能表不存在: {}", tableName, err.getMessage());
                promise.complete(new HashSet<>()); // 返回空集合，触发创建表逻辑
            });
        
        return promise.future();
    }



    /**
     * 添加新字段（只处理带 @NewField 注解的字段）
     */
    private static Future<Void> addNewFields(Pool pool, Class<?> clazz, String tableName,
                                             List<Field> newFields, Set<String> existingColumns,
                                             JDBCType type) {
        List<Future<Void>> futures = new ArrayList<>();
        
        Case caseFormat = SnakeCase.INSTANCE;
        if (clazz.isAnnotationPresent(RowMapped.class)) {
            RowMapped annotation = clazz.getAnnotation(RowMapped.class);
            caseFormat = getCase(annotation.formatter());
        }
        
        String quotationMarks = type == JDBCType.MySQL ? "`" : "\"";
        
        for (Field field : newFields) {
            // 获取字段名
            String columnName;
            if (field.isAnnotationPresent(Column.class)) {
                Column annotation = field.getAnnotation(Column.class);
                columnName = StringUtils.isNotEmpty(annotation.name())
                    ? annotation.name()
                    : LowerCamelCase.INSTANCE.to(caseFormat, field.getName());
            } else {
                columnName = LowerCamelCase.INSTANCE.to(caseFormat, field.getName());
            }
            
            // 检查字段是否已存在
            if (existingColumns.contains(columnName.toLowerCase())) {
                log.warn("字段 '{}' 已存在，请移除 @NewField 注解", columnName);
                continue;
            }
            
            // 生成 ALTER TABLE 语句
            String sql = buildAlterTableSQL(tableName, field, columnName, quotationMarks, type);
            
            log.info("添加字段: {}", sql);
            
            Promise<Void> p = Promise.promise();
            pool.query(sql).execute()
                .onSuccess(v -> {
                    log.info("字段 '{}' 添加成功", columnName);
                    p.complete();
                })
                .onFailure(err -> {
                    String errorMsg = err.getMessage();
                    // 如果字段已存在，忽略错误（可能是并发执行或检测失败）
                    if (errorMsg != null && (errorMsg.contains("Duplicate column") || 
                                            errorMsg.contains("already exists") ||
                                            errorMsg.contains("duplicate key"))) {
                        log.warn("字段 '{}' 已存在，跳过添加", columnName);
                        p.complete();
                    } else {
                        log.error("字段 '{}' 添加失败", columnName, err);
                        p.fail(err);
                    }
                });
            
            futures.add(p.future());
        }
        
        return Future.all(futures).mapEmpty();
    }

    /**
     * 构建 ALTER TABLE 添加字段的 SQL
     */
    private static String buildAlterTableSQL(String tableName, Field field, String columnName,
                                             String quotationMarks, JDBCType type) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(quotationMarks).append(tableName).append(quotationMarks)
          .append(" ADD COLUMN ").append(quotationMarks).append(columnName).append(quotationMarks);
        
        // 获取字段类型
        String sqlType = CreateTable.javaProperty2SqlColumnMap.get(field.getType());
        if (sqlType == null) {
            sqlType = "VARCHAR";
        }
        sb.append(" ").append(sqlType);
        
        // 添加类型长度
        int[] decimalSize = {22, 2};
        int varcharSize = 255;
        if (field.isAnnotationPresent(Length.class)) {
            Length length = field.getAnnotation(Length.class);
            decimalSize = length.decimalSize();
            varcharSize = length.varcharSize();
        }
        
        if ("DECIMAL".equals(sqlType)) {
            sb.append("(").append(decimalSize[0]).append(",").append(decimalSize[1]).append(")");
        } else if ("VARCHAR".equals(sqlType)) {
            sb.append("(").append(varcharSize).append(")");
        }
        
        // 添加约束
        if (field.isAnnotationPresent(Constraint.class)) {
            Constraint constraint = field.getAnnotation(Constraint.class);
            
            if (constraint.notNull()) {
                sb.append(" NOT NULL");
            }
            
            if (StringUtils.isNotEmpty(constraint.defaultValue())) {
                String apostrophe = constraint.defaultValueIsFunction() ? "" : "'";
                sb.append(" DEFAULT ").append(apostrophe).append(constraint.defaultValue()).append(apostrophe);
            }
        }
        
        return sb.toString();
    }

    /**
     * 判断是否忽略字段
     */
    private static boolean isIgnoredField(Field field) {
        int modifiers = field.getModifiers();
        return java.lang.reflect.Modifier.isStatic(modifiers)
            || java.lang.reflect.Modifier.isTransient(modifiers)
            || field.isAnnotationPresent(TableGenIgnore.class);
    }

    /**
     * 获取 Case 类型
     */
    private static Case getCase(Class<?> clz) {
        return switch (clz.getName()) {
            case "io.vertx.codegen.format.CamelCase" -> io.vertx.codegen.format.CamelCase.INSTANCE;
            case "io.vertx.codegen.format.SnakeCase" -> SnakeCase.INSTANCE;
            case "io.vertx.codegen.format.LowerCamelCase" -> LowerCamelCase.INSTANCE;
            default -> SnakeCase.INSTANCE;
        };
    }
}
