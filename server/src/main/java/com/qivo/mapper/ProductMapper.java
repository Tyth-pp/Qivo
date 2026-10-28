package com.qivo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qivo.entity.Product;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    List<Product> selectPage(
            IPage<?> page,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("status") Integer status,
            @Param("sort") String sort);

    @Select("SELECT * FROM product WHERE id = #{id} FOR UPDATE")
    Product lockById(@Param("id") Long id);

    @Update("UPDATE product SET sales=COALESCE(sales,0)+#{quantity} WHERE id=#{id}")
    int increaseSales(@Param("id") Long id, @Param("quantity") int quantity);

    @Update("UPDATE product SET sales=sales-#{quantity} WHERE id=#{id} AND sales>=#{quantity}")
    int reduceSales(@Param("id") Long id,@Param("quantity") int quantity);

    int updateProduct(Product product);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
