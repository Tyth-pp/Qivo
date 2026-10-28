package com.qivo.mapper;

import com.qivo.entity.Address;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface AddressMapper {
    List<Address> selectByUserId(@Param("userId") Long userId);

    Address selectById(@Param("id") Long id);

    int insert(Address address);

    int update(Address address);

    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    int clearDefaultByUserId(@Param("userId") Long userId);

    int setDefault(@Param("id") Long id);
}
