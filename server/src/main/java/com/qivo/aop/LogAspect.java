package com.qivo.aop;

import com.qivo.annotation.Log;
import com.qivo.constant.SecurityConstant;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作日志切面 —— 拦截 @Log 注解的方法，记录操作人、IP、参数、耗时
 */
@Slf4j
@Aspect
@Component
public class LogAspect {

    @Pointcut("@annotation(com.qivo.annotation.Log)")
    public void logPointcut() {}

    @Around("logPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } finally {
            long elapsed = System.currentTimeMillis() - start;

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Log logAnnotation = method.getAnnotation(Log.class);

            String username = "anonymous";
            String ip = "unknown";
            try {
                ServletRequestAttributes attrs =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest request = attrs.getRequest();
                    username = (String) request.getAttribute(SecurityConstant.REQUEST_USERNAME);
                    if (username == null) username = "anonymous";
                    ip = request.getRemoteAddr();
                }
            } catch (Exception ignored) {
            }

            log.info(
                    "[操作日志] 操作人={} 类型={} 内容={} IP={} 结果={} 耗时={}ms",
                    username,
                    logAnnotation.type(),
                    logAnnotation.value(),
                    ip,
                    success ? "SUCCESS" : "FAILED",
                    elapsed);
        }
    }
}
