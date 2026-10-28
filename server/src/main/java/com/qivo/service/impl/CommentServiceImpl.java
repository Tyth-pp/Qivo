package com.qivo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qivo.constant.CommentMessageConstant;
import com.qivo.entity.Order;
import com.qivo.entity.OrderItem;
import com.qivo.entity.ProductComment;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.OrderItemMapper;
import com.qivo.mapper.OrderMapper;
import com.qivo.mapper.ProductCommentMapper;
import com.qivo.persistence.PageRequests;
import com.qivo.result.ErrorCode;
import com.qivo.service.CommentService;
import com.qivo.vo.PageVO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentServiceImpl implements CommentService {

    private final ProductCommentMapper commentMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public CommentServiceImpl(
            ProductCommentMapper commentMapper,
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper) {
        this.commentMapper = commentMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    public PageVO<ProductComment> pageByProductId(Long productId, int page, int pageSize) {
        Page<ProductComment> pagination = PageRequests.of(page, pageSize);
        List<ProductComment> list = commentMapper.selectByProductId(pagination, productId);
        return PageVO.of(list, pagination.getTotal(), page, pageSize);
    }

    @Override
    @Transactional
    public void add(ProductComment comment) {
        if (comment.getRating() == null || comment.getRating() < 1 || comment.getRating() > 5) {
            throw new BusinessException(CommentMessageConstant.INVALID_RATING);
        }
        if (comment.getOrderId() == null || comment.getProductId() == null) {
            throw new BusinessException(CommentMessageConstant.ORDER_INFORMATION_REQUIRED);
        }
        Order order = orderMapper.selectById(comment.getOrderId());
        if (order == null || !order.getUserId().equals(comment.getUserId())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_EXIST);
        }
        if (order.getShipStatus() == null || order.getShipStatus() != 2) {
            throw new BusinessException(CommentMessageConstant.RECEIPT_REQUIRED);
        }
        boolean productInOrder = false;
        for (OrderItem item : orderItemMapper.selectByOrderId(comment.getOrderId())) {
            if (item.getProductId().equals(comment.getProductId())) {
                productInOrder = true;
                break;
            }
        }
        if (!productInOrder) {
            throw new BusinessException(CommentMessageConstant.PRODUCT_NOT_IN_ORDER);
        }
        if (commentMapper.selectByOrderAndProduct(comment.getOrderId(), comment.getProductId())
                != null) {
            throw new BusinessException(CommentMessageConstant.ALREADY_REVIEWED);
        }
        commentMapper.insert(comment);
    }

    @Override
    @Transactional
    public void append(Long userId, Long commentId, String appendContent) {
        ProductComment comment = commentMapper.selectById(commentId);
        if (comment == null || !comment.getUserId().equals(userId)) {
            throw new BusinessException(CommentMessageConstant.COMMENT_NOT_FOUND);
        }
        if (comment.getAppendContent() != null && !comment.getAppendContent().isBlank()) {
            throw new BusinessException(CommentMessageConstant.ALREADY_APPENDED);
        }
        if (appendContent == null || appendContent.isBlank()) {
            throw new BusinessException(CommentMessageConstant.APPEND_CONTENT_REQUIRED);
        }
        commentMapper.updateAppend(commentId, appendContent);
    }
}
