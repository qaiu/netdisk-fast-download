package cn.qaiu.vx.core.annotaions;

import java.lang.annotation.*;

@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandleSortFilter {
    /**
     * 注册顺序，数字越大越先注册<br>
     * 前置拦截器会先执行后注册即数字小的, 后置拦截器会先执行先注册的即数字大的<br>
     * 值<0时会过滤掉该处理器
     */
    int value() default 0;
}
