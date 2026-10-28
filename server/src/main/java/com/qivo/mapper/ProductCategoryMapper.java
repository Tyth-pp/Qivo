package com.qivo.mapper;

import com.qivo.entity.ProductCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProductCategoryMapper {
    List<ProductCategory> selectAll();

    List<ProductCategory> selectByParentId(@Param("parentId") Long parentId);

    ProductCategory selectById(@Param("id") Long id);

    int insert(ProductCategory category);

    int update(ProductCategory category);
}
