package com.qivo.mapper;

import com.qivo.entity.Role;
import com.qivo.vo.RoleMemberVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RoleMapper {
    @Select("SELECT * FROM role WHERE code=#{code}")
    Role byCode(@Param("code") String code);

    @Update("UPDATE role SET version=version+1,update_time=NOW() WHERE id=#{id} AND version=#{version}")
    int advanceVersion(@Param("id") Long id, @Param("version") Long version);

    @Select("SELECT COUNT(DISTINCT a.id) FROM admin a JOIN admin_role ar ON ar.admin_id=a.id WHERE ar.role_id=#{id}")
    long memberCount(@Param("id") Long id);

    @Select("""
            <script>
            SELECT a.id,a.username,a.real_name,a.avatar,a.status,a.last_login_time FROM admin a
            WHERE EXISTS(SELECT 1 FROM admin_role ar WHERE ar.admin_id=a.id AND ar.role_id=#{roleId})
            <if test="keyword != null and keyword != ''">
                AND (a.username LIKE CONCAT('%',#{keyword},'%') OR a.real_name LIKE CONCAT('%',#{keyword},'%'))
            </if>
            <if test="status != null">AND a.status=#{status}</if>
            ORDER BY a.id
            </script>
            """)
    List<RoleMemberVO> members(IPage<?> page, @Param("roleId") Long roleId,
            @Param("keyword") String keyword, @Param("status") Integer status);

    @Select("SELECT a.id FROM admin a JOIN admin_role ar ON ar.admin_id=a.id JOIN role r ON r.id=ar.role_id WHERE r.code='super_admin' ORDER BY a.id FOR UPDATE")
    List<Long> lockSuperAdmins();
    @Select("SELECT COUNT(*) FROM admin a JOIN admin_role ar ON ar.admin_id=a.id JOIN role r ON r.id=ar.role_id WHERE r.code='super_admin' AND a.status=1")
    int enabledSuperAdmins();
    @Select("SELECT * FROM role WHERE id=#{id} FOR UPDATE")
    Role lockById(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM admin_role WHERE role_id=#{id}")
    int bindings(@Param("id") Long id);
    @Delete("DELETE FROM admin_role WHERE admin_id=#{adminId}")
    int deleteAdminRoles(@Param("adminId") Long adminId);

    @Insert("INSERT INTO admin_role(admin_id,role_id) VALUES(#{adminId},#{roleId})")
    int insertAdminRole(@Param("adminId") Long adminId, @Param("roleId") Long roleId);

    List<Role> selectAll();

    Role selectById(@Param("id") Long id);

    int insert(Role role);

    int update(Role role);

    int deleteById(@Param("id") Long id);

    List<Role> selectByAdminId(@Param("adminId") Long adminId);
}
