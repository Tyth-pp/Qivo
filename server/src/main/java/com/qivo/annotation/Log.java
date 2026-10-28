package com.qivo.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解 —— 标记在 Controller 方法上，AOP 自动记录操作日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    /** 操作内容 */
    String value() default "";

    /** 操作类型：INSERT / UPDATE / DELETE / SELECT / LOGIN */
    String type() default "SELECT";
}
