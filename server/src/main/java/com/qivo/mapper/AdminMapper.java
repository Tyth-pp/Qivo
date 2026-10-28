package com.qivo.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qivo.entity.Admin;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminMapper {
    @Select("SELECT * FROM admin WHERE id=#{id} FOR UPDATE")
    Admin lockById(@Param("id") Long id);

    List<Admin> selectAll(IPage<?> page);

    List<Admin> search(IPage<?> page,@Param("keyword") String keyword,@Param("status") Integer status);

    Admin selectById(@Param("id") Long id);

    Admin selectByUsername(@Param("username") String username);

    int insert(Admin admin);

    int update(Admin admin);

    @Update("UPDATE admin SET real_name=COALESCE(#{realName},real_name), avatar=COALESCE(#{avatar},avatar), update_time=NOW() WHERE id=#{id}")
    int updateProfile(@Param("id") Long id, @Param("realName") String realName, @Param("avatar") String avatar);

    @Update(
            "UPDATE admin SET password=#{password}, update_time=NOW() WHERE id=#{id} AND password=#{oldPassword}")
    int changePasswordIfUnchanged(
            @Param("id") Long id,
            @Param("oldPassword") String oldPassword,
            @Param("password") String password);

    int updatePassword(@Param("id") Long id, @Param("password") String password);
}
