package com.qivo.vo;
import com.qivo.entity.Admin;
import com.qivo.entity.Role;
import org.springframework.beans.BeanUtils;
import java.util.List;
public class AdminAccountVO extends Admin {
    private final List<Role> roles;
    public AdminAccountVO(Admin admin,List<Role> roles) {BeanUtils.copyProperties(admin,this);setPassword(null);this.roles=List.copyOf(roles);}
    public List<Role> getRoles(){return roles;}
}
