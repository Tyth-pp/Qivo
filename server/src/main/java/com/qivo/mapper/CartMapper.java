package com.qivo.mapper;

import com.qivo.entity.Cart;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CartMapper {
    List<Cart> selectByUserId(@Param("userId") Long userId);

    List<Cart> selectCheckedByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM cart WHERE user_id=#{userId} ORDER BY id FOR UPDATE")
    List<Cart> lockByUser(@Param("userId") Long userId);

    @Select("SELECT id FROM user WHERE id=#{userId} FOR UPDATE")
    Long lockOwner(@Param("userId") Long userId);

    int upsert(Cart cart);

    int updateQuantity(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("quantity") Integer quantity);

    int updateChecked(
            @Param("id") Long id, @Param("userId") Long userId, @Param("checked") Integer checked);

    int deleteById(@Param("id") Long id, @Param("userId") Long userId);
}
