package com.qivo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qivo.entity.ProductSku;
import com.qivo.vo.SkuProductSummaryVO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {
    @Select("SELECT * FROM product_sku WHERE product_id = #{productId} ORDER BY id")
    List<ProductSku> listByProduct(@Param("productId") Long productId);

    @Select("SELECT * FROM product_sku WHERE id = #{id} FOR UPDATE")
    ProductSku lockById(@Param("id") Long id);

    @Update(
            "UPDATE product_sku SET stock=stock-#{quantity} WHERE id=#{id} AND status='ENABLED' AND"
                + " stock >= #{quantity}")
    int deduct(@Param("id") Long id, @Param("quantity") int quantity);

    @Update(
            "UPDATE product_sku SET stock=stock+#{delta} WHERE id=#{id} AND stock+CAST(#{delta} AS"
                + " SIGNED) BETWEEN 0 AND 2147483647")
    int adjust(@Param("id") Long id, @Param("delta") long delta);

    @Update(
            "UPDATE product_sku SET price=#{price}, original_price=#{originalPrice},"
                + " status=#{status}, image=#{image} WHERE id=#{id}")
    int updateDetails(ProductSku sku);

    List<SkuProductSummaryVO> selectSummaries(
            IPage<?> page,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("status") Integer status,
            @Param("sort") String sort);
}
