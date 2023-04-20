package cn.qaiu.vx.core.annotaions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 日期参数格式化注解
 * <br>Create date 2021-05-06 09:20:37
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface DateFormat {
    String value() default "yyyy-MM-dd HH:mm:ss";
}
