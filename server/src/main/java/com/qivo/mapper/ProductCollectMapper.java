package com.qivo.mapper;

import com.qivo.entity.ProductCollect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProductCollectMapper {
    List<ProductCollect> selectByUserId(@Param("userId") Long userId);

    int exists(@Param("userId") Long userId, @Param("productId") Long productId);

    int insert(@Param("userId") Long userId, @Param("productId") Long productId);

    int delete(@Param("userId") Long userId, @Param("productId") Long productId);
}
