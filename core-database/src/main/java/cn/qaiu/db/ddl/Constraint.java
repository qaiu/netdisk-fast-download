package cn.qaiu.db.ddl;

import java.lang.annotation.*;

/**
 * 建表约束类型
 *
 * <br>Create date 2021/7/22 0:42
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Constraint {

    /**
     * 非空约束
     * @return false 可以为空
     */
    boolean notNull() default false;

    /**
     * 唯一键约束 TODO 待实现
     * @return 唯一键约束
     */
    String uniqueKey() default "";

    /**
     * 默认值约束
     * @return 默认值约束
     */
    String defaultValue() default "";
    /**
     * 默认值是否是函数
     * @return false 不是函数
     */
    boolean defaultValueIsFunction() default false;

    /**
     * 是否自增 只能用于int或long类型上
     * @return false 不自增
     */
    boolean autoIncrement() default false;
}
