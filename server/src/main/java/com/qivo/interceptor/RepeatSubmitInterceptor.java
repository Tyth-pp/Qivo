package com.qivo.interceptor;

import com.qivo.annotation.RepeatSubmit;
import com.qivo.cache.RedisStore;
import com.qivo.constant.RequestMessageConstant;
import com.qivo.constant.SecurityConstant;
import com.qivo.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 防重复提交拦截器 —— 配合 @RepeatSubmit 注解
 * 原理：请求前检查 Redis 是否有该 key，没有则写入，有则拦截
 */
@Slf4j
@Component
public class RepeatSubmitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "repeat_submit:";

    private final RedisStore redisStore;

    public RepeatSubmitInterceptor(RedisStore redisStore) {
        this.redisStore = redisStore;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RepeatSubmit annotation = handlerMethod.getMethodAnnotation(RepeatSubmit.class);
        if (annotation == null) {
            return true;
        }

        String key = buildKey(request);
        if (!redisStore.setIfAbsent(key, "1", annotation.expire(), TimeUnit.MILLISECONDS)) {
            throw new BusinessException(RequestMessageConstant.DUPLICATE_SUBMISSION);
        }

        return true;
    }

    private String buildKey(HttpServletRequest request) {
        Object userId = request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        String uid = userId != null ? String.valueOf(userId) : "anon";
        return KEY_PREFIX + uid + ":" + request.getRequestURI() + ":" + request.getMethod();
    }
}
