package cn.qaiu.db.ddl;

import java.lang.annotation.*;

/**
 * 标注建表的实体类和主键字段
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})//次注解作用于类和字段上
public @interface Table {
    String value() default ""; //默认表名为空
    String keyFields() default "id"; //默认主键为id
}
