package com.qivo.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qivo.entity.RoleChange;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RoleChangeMapper {
    @Insert("""
            INSERT INTO role_change_log(role_id,role_name,action,actor_id,actor_name,
                before_snapshot,after_snapshot,note)
            VALUES(#{roleId},#{roleName},#{action},#{actorId},#{actorName},
                #{beforeSnapshot},#{afterSnapshot},#{note})
            """)
    int insert(RoleChange change);

    @Select("""
            <script>
            SELECT * FROM role_change_log WHERE 1=1
            <if test="roleId != null">AND role_id=#{roleId}</if>
            <if test="operator != null and operator != ''">
                AND actor_name LIKE CONCAT('%',#{operator},'%')
            </if>
            <if test="from != null">AND create_time &gt;= #{from}</if>
            <if test="to != null">AND create_time &lt; #{to}</if>
            ORDER BY id DESC
            </script>
            """)
    List<RoleChange> search(IPage<?> page, @Param("roleId") Long roleId,
            @Param("operator") String operator, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
