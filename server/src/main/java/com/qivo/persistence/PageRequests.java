package com.qivo.persistence;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qivo.exception.BusinessException;
import com.qivo.result.ErrorCode;

public final class PageRequests {
    private PageRequests() {}

    public static <T> Page<T> of(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new BusinessException(ErrorCode.BAD_REQUEST);
        return new Page<>(page, size);
    }
}
