package cn.qaiu.db.ddl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标识新增字段，用于数据库表结构迁移
 * 只有带此注解的字段才会被 SchemaMigration 检查和添加
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>在现有实体类中添加新字段时，使用此注解标记</li>
 *   <li>应用启动时会自动检测并添加到数据库表中</li>
 *   <li>添加成功后可以移除此注解，避免重复检查</li>
 * </ul>
 * 
 * <p>示例：</p>
 * <pre>{@code
 * @Data
 * @Table("users")
 * public class User {
 *     private Long id;
 *     private String name;
 *     
 *     @NewField  // 标记为新增字段
 *     @Length(varcharSize = 32)
 *     @Constraint(defaultValue = "active")
 *     private String status;
 * }
 * }</pre>
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NewField {
    
    /**
     * 字段描述（可选）
     */
    String value() default "";
}
