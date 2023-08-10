package cn.qaiu.vx.core.annotaions;

import java.lang.annotation.*;

/**
 * 拦截器配置注解
 * 正则匹配拦截途径
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InterceptorConfig {

    String pattern() default "";

    /**
     * 注册顺序，数字越大越先注册
     */
    int order() default 0;
}
