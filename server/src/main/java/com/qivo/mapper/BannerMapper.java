package com.qivo.mapper;

import com.qivo.entity.Banner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BannerMapper {
    List<Banner> selectAllEnabled();

    List<Banner> selectAll();

    Banner selectById(@Param("id") Long id);

    int insert(Banner banner);

    int update(Banner banner);

    int deleteById(@Param("id") Long id);
}
