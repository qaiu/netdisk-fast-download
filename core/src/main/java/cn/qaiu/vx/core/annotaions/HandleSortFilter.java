package cn.qaiu.vx.core.annotaions;

import java.lang.annotation.*;

@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandleSortFilter {
    /**
     * 注册顺序，数字越大越先注册<br>
     * 值<0时会过滤掉该处理器
     */
    int value() default 0;
}
