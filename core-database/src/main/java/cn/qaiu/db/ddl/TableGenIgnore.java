package cn.qaiu.db.ddl;

import java.lang.annotation.*;

/**
 * 建表时忽略字段
 * <br>Create date 2021/8/27 15:49
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface TableGenIgnore {
}
