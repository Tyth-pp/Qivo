package com.qivo.service;

import com.qivo.entity.ProductComment;
import com.qivo.vo.PageVO;

public interface CommentService {
    PageVO<ProductComment> pageByProductId(Long productId, int page, int pageSize);

    void add(ProductComment comment);

    void append(Long userId, Long commentId, String appendContent);
}
