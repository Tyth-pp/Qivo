package com.qivo.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qivo.entity.RefundAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
@Mapper
public interface RefundAttemptMapper extends BaseMapper<RefundAttempt> {
    @Select("SELECT * FROM refund_attempt WHERE after_sale_id=#{id} ORDER BY attempt_no FOR UPDATE")
    List<RefundAttempt> forAfterSale(@Param("id") Long id);
}
