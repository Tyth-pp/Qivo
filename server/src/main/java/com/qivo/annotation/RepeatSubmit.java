package com.qivo.annotation;

import java.lang.annotation.*;

/**
 * 防重复提交注解 —— 配合 RepeatSubmitInterceptor 使用
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepeatSubmit {

    /** 防重有效期，单位毫秒 */
    long expire() default 3000;
}
