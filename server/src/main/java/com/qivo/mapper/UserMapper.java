package com.qivo.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qivo.entity.User;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);

    User selectByUsername(@Param("username") String username);

    List<User> selectPage(IPage<?> page, @Param("keyword") String keyword);

    int insert(User user);

    int update(User user);

    @Update("UPDATE user SET password=#{password}, update_time=NOW() WHERE id=#{id} AND password=#{oldPassword}")
    int changePasswordIfUnchanged(@Param("id") Long id, @Param("oldPassword") String oldPassword,
            @Param("password") String password);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateScore(@Param("id") Long id, @Param("score") Integer score);
}
