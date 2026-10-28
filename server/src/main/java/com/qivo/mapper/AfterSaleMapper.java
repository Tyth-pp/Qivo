package com.qivo.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qivo.entity.AfterSale;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;
@Mapper
public interface AfterSaleMapper extends BaseMapper<AfterSale> {
    @Select("SELECT * FROM after_sale WHERE id=#{id} FOR UPDATE")
    AfterSale lockById(@Param("id") Long id);
    @Select("SELECT * FROM after_sale WHERE order_id=#{orderId} ORDER BY id DESC")
    List<AfterSale> forOrder(@Param("orderId") Long orderId);
    @Select("SELECT * FROM after_sale WHERE user_id=#{userId} AND request_key=#{key}")
    AfterSale byRequest(@Param("userId") Long userId,@Param("key") String key);
    @Select("SELECT COUNT(*) FROM after_sale WHERE order_id=#{orderId} AND status IN ('REQUESTED','REFUNDING','REFUND_FAILED')")
    int activeCount(@Param("orderId") Long orderId);
    @Update("UPDATE after_sale SET status=#{status},review_note=#{reviewNote},reviewer_id=#{reviewerId},refunded_order_id=#{refundedOrderId},update_time=NOW() WHERE id=#{id}")
    int saveReview(AfterSale row);
}
