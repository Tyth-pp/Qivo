package com.qivo.handler;

import com.qivo.constant.RequestMessageConstant;
import com.qivo.exception.BusinessException;
import com.qivo.result.ErrorCode;
import com.qivo.result.Result;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import java.util.Map;
import java.util.Comparator;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 全局异常处理器 —— 捕获所有异常，统一返回 Result JSON */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> duplicateKey(DuplicateKeyException failure) {
        return Result.fail(ErrorCode.CONFLICT);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> invalidConstraint(ConstraintViolationException failure) {
        return Result.fail(ErrorCode.BAD_REQUEST);
    }

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(
            BusinessException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("业务异常 [{}] {} -> {}", e.getCode(), request.getRequestURI(), e.getMessage());
        int code = e.getCode();
        int status =
                code >= 400 && code <= 599
                        ? code
                        : (code == 1001 || code == 2001 || code == 3001 || code == 4001
                                ? 404
                                : (code == 1002 || code == 1003 ? 401 : 409));
        response.setStatus(status);
        return Result.fail(code, e.getMessage());
    }

    /** 参数校验异常 */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        String msg =
                e.getBindingResult().getFieldErrors().stream()
                        .sorted(Comparator.comparing(FieldError::getField))
                        .map(GlobalExceptionHandler::validationMessage)
                        .distinct()
                        .reduce((a, b) -> a + "; " + b)
                        .orElse(RequestMessageConstant.VALIDATION_FAILED);
        log.warn("参数校验异常 [{}] {}", request.getRequestURI(), msg);
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    /** 缺少请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParamException(MissingServletRequestParameterException e) {
        return Result.fail(
                ErrorCode.BAD_REQUEST.getCode(),
                RequestMessageConstant.VALIDATION_FAILED);
    }

    /** 请求方式不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e) {
        return Result.fail(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> denied() {
        return Result.fail(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler({
        MissingRequestHeaderException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> malformed() {
        return Result.fail(ErrorCode.BAD_REQUEST);
    }

    /** 兜底：系统异常 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 [{}] {}", request.getRequestURI(), e.getMessage(), e);
        return Result.fail(ErrorCode.FAIL);
    }

    private static String validationMessage(FieldError error) {
        String field = error.getField();
        String label = Map.ofEntries(
                Map.entry("username", "用户名"), Map.entry("password", "密码"),
                Map.entry("oldPassword", "当前密码"), Map.entry("newPassword", "新密码"),
                Map.entry("email", "邮箱"), Map.entry("phone", "联系电话"),
                Map.entry("realName", "显示名称"), Map.entry("avatar", "头像"),
                Map.entry("captchaCode", "验证码"), Map.entry("captchaKey", "验证码"),
                Map.entry("quantity", "数量"), Map.entry("status", "状态"),
                Map.entry("reason", "申请原因"), Map.entry("note", "处理说明"),
                Map.entry("remark", "订单备注"), Map.entry("roleId", "角色"),
                Map.entry("permissionIds", "权限选择"), Map.entry("orderId", "订单"),
                Map.entry("addressId", "收货地址"), Map.entry("skuId", "商品规格"))
                .getOrDefault(field, "填写内容");
        String message = error.getDefaultMessage();
        if (message == null || message.contains("正则") || message.contains("regexp"))
            return label + "格式不正确，请检查后重试";
        return message.contains(label) ? message : label + "：" + message;
    }
}

