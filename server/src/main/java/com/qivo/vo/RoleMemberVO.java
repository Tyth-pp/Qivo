package com.qivo.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 关联账号只提供识别与状态字段，不加载密码或会话信息。 */
@Data
public class RoleMemberVO {
    private Long id;
    private String username;
    private String realName;
    private String avatar;
    private Integer status;
    private LocalDateTime lastLoginTime;
}
