package com.qivo.constant;

/** SKU 模型边界；迁移中的固定编码需与此保持一致。 */
public final class SkuConstant {
    private SkuConstant() {}

    public static final String DEFAULT_SPEC_KEY = "DEFAULT";
    public static final String DEFAULT_CODE_PREFIX = "QIVO-DEFAULT-";
    public static final int MAX_DIMENSIONS = 3;
    public static final int MAX_VALUES_PER_DIMENSION = 10;
    public static final int MAX_SKUS = 100;
    public static final int MAX_LABEL_LENGTH = 30;
}
