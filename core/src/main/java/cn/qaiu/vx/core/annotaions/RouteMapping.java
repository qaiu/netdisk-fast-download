package cn.qaiu.vx.core.annotaions;

import cn.qaiu.vx.core.enums.MIMEType;
import cn.qaiu.vx.core.enums.RouteMethod;

import java.lang.annotation.*;

/**
 * Router API Method 标识注解
 * <br>Create date 2021-05-06 09:20:37
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RouteMapping {

    String value() default "";

    /**
     * 使用http method
     */
    RouteMethod method() default RouteMethod.GET;

    /**
     * 接口description
     */
    String description() default "";

    /**
     * 注册顺序，数字越大越先注册
     */
    int order() default 0;

    /**
     * 响应类型
     */
    MIMEType responseMimeType() default MIMEType.APPLICATION_JSON;

    /**
     * 请求类型
     */
    MIMEType requestMIMEType() default MIMEType.ALL;
}
