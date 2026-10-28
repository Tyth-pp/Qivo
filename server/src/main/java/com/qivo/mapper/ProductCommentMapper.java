package com.qivo.mapper;

import com.qivo.entity.ProductComment;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProductCommentMapper {
    List<ProductComment> selectByProductId(IPage<?> page, @Param("productId") Long productId);

    List<ProductComment> selectByOrderId(@Param("orderId") Long orderId);

    ProductComment selectById(@Param("id") Long id);

    ProductComment selectByOrderAndProduct(
            @Param("orderId") Long orderId, @Param("productId") Long productId);

    int insert(ProductComment comment);

    int updateAppend(@Param("id") Long id, @Param("appendContent") String appendContent);
}
