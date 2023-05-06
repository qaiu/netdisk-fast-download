package cn.qaiu.db.ddl;

import java.lang.annotation.*;

/**
 * 字段长度属性
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Length {
    int[] decimalSize() default {22,2}; //bigDecimal精度
    int varcharSize() default 255; //varchar大小
}
