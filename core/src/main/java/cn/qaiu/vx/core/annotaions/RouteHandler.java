package cn.qaiu.vx.core.annotaions;

import java.lang.annotation.*;

/**
 * Web Router API类 标识注解
 * <br>Create date 2021-04-30 09:22:18
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RouteHandler {

    String value() default "";

    boolean isOpen() default false;

    /**
     * 注册顺序，数字越大越先注册
     */
    int order() default 0;
}
