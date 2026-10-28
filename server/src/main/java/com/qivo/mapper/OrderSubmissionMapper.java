package com.qivo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qivo.entity.OrderSubmission;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderSubmissionMapper extends BaseMapper<OrderSubmission> {
    // 调用方先取得用户行锁；此处普通读取，避免缺失记录的间隙锁与库存锁交叉。
    @Select("SELECT * FROM order_submission WHERE user_id=#{userId} AND request_key=#{key}")
    OrderSubmission find(@Param("userId") Long userId, @Param("key") String key);
}
