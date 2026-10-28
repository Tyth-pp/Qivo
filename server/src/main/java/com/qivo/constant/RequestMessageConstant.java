package com.qivo.constant;

/** 请求校验与重复提交的公共提示。 */
public final class RequestMessageConstant {

    private RequestMessageConstant() {}

    public static final String DUPLICATE_SUBMISSION = "请勿重复提交，请稍后再试";
    public static final String VALIDATION_FAILED = "填写内容不符合要求，请检查后重试";
    public static final String MISSING_PARAMETER_PREFIX = "缺少参数: ";
}

