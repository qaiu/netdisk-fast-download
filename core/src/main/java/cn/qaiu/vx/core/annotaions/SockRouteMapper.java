package cn.qaiu.vx.core.annotaions;

import java.lang.annotation.*;

/**
 * WebSocket api 注解类
 * <br>Create date 2021/8/25 15:57
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Documented
@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SockRouteMapper {
    String value() default "";
}
