package com.qivo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qivo.constant.SecurityConstant;
import com.qivo.utils.AvatarFiles;
import com.qivo.entity.Admin;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.AdminMapper;
import com.qivo.mapper.RoleMapper;
import com.qivo.persistence.PageRequests;
import com.qivo.result.ErrorCode;
import com.qivo.security.JwtTokenProvider;
import com.qivo.security.Passwords;
import com.qivo.service.AdminService;
import com.qivo.vo.PageVO;
import com.qivo.vo.AdminAccountVO;
import com.qivo.dto.AdminAccountRequest;
import com.qivo.dto.AdminAccountUpdateRequest;
import com.qivo.security.SessionRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;
    private final RoleMapper roleMapper;
    private final JwtTokenProvider jwtTokens;
    private final Passwords passwords;
    private final SessionRegistry sessions;

    public AdminServiceImpl(
            AdminMapper adminMapper,
            RoleMapper roleMapper,
            JwtTokenProvider jwtTokens,
            Passwords passwords,SessionRegistry sessions) {
        this.sessions=sessions;
        this.adminMapper = adminMapper;
        this.roleMapper = roleMapper;
        this.jwtTokens = jwtTokens;
        this.passwords = passwords;
    }

    @Override
    public Admin getById(Long id) {
        return adminMapper.selectById(id);
    }

    @Override
    @Transactional
    public void updateProfile(Long id, String realName, String avatar) {
        Admin existing = adminMapper.selectById(id);
        if (existing == null) throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        if (avatar != null && !avatar.equals(existing.getAvatar())) AvatarFiles.validatePath(avatar);
        if (realName != null && realName.length() > 50) throw new BusinessException(ErrorCode.BAD_REQUEST);
        adminMapper.updateProfile(id, realName, avatar);
    }

    @Override
    public Map<String, Object> login(String username, String password) {
        Admin admin = adminMapper.selectByUsername(username);
        if (admin == null) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        if (admin.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_ACCOUNT_DISABLED);
        }
        if (!passwords.matches(password, admin.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }
        String token =
                jwtTokens.generateToken(
                        admin.getId(),
                        admin.getUsername(),
                        SecurityConstant.DOMAIN_ADMIN,
                        admin.getPassword());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("adminId", admin.getId());
        result.put("username", admin.getUsername());
        result.put("realName", admin.getRealName());
        result.put("avatar", admin.getAvatar());
        result.put("roles", roleMapper.selectByAdminId(admin.getId()));
        return result;
    }

    @Override
    @Transactional
    public void updatePassword(Long adminId, String oldPassword, String newPassword) {
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        if (!passwords.matches(oldPassword, admin.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }
        if (passwords.matches(newPassword, admin.getPassword())) throw new BusinessException(ErrorCode.BAD_REQUEST);
        if (adminMapper.changePasswordIfUnchanged(
                        adminId, admin.getPassword(), passwords.encode(newPassword))
                != 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    @Override
    public PageVO<Admin> list(int page, int pageSize) {
        Page<Admin> pagination = PageRequests.of(page, pageSize);
        List<Admin> list = adminMapper.selectAll(pagination);
        return PageVO.of(list, pagination.getTotal(), page, pageSize);
    }

    @Override
    @Transactional
    public void add(Admin admin) {
        Admin exist = adminMapper.selectByUsername(admin.getUsername());
        if (exist != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        admin.setPassword(passwords.encode(admin.getPassword()));
        admin.setStatus(1);
        adminMapper.insert(admin);
    }

    @Override
    @Transactional
    public void assignRole(Long adminId, Long roleId) {
        roleMapper.lockSuperAdmins();
        var role=roleMapper.lockById(roleId);
        if (role == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        var account=adminMapper.lockById(adminId);
        if (account==null) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (!SecurityConstant.BUILTIN_SUPER_ADMIN_CODE.equals(role.getCode())) protectLastSuper(account);
        roleMapper.deleteAdminRoles(adminId);
        roleMapper.insertAdminRole(adminId, roleId);
    }

    private void protectLastSuper(Admin admin) {
        boolean isSuper=roleMapper.selectByAdminId(admin.getId()).stream()
                .anyMatch(role->SecurityConstant.BUILTIN_SUPER_ADMIN_CODE.equals(role.getCode()));
        if (isSuper && admin.getStatus()==1 && roleMapper.enabledSuperAdmins()<=1)
            throw new BusinessException(409,"不能停用或移除最后一个可用超级管理员");
    }

    @Override
    public PageVO<AdminAccountVO> accounts(int page,int pageSize,String keyword,Integer status) {
        if(status!=null&&status!=0&&status!=1)throw new BusinessException(ErrorCode.BAD_REQUEST);
        var pagination=PageRequests.<Admin>of(page,pageSize);
        var rows=adminMapper.search(pagination,keyword,status).stream()
                .map(admin->new AdminAccountVO(admin,roleMapper.selectByAdminId(admin.getId()))).toList();
        return PageVO.of(rows,pagination.getTotal(),page,pageSize);
    }

    @Override @Transactional
    public void createAccount(AdminAccountRequest request) {
        if(request.roleId()!=null&&roleMapper.lockById(request.roleId())==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        var admin=new Admin();admin.setUsername(request.username());admin.setPassword(request.password());admin.setRealName(request.realName());
        add(admin);
        if(request.roleId()!=null)roleMapper.insertAdminRole(admin.getId(),request.roleId());
    }

    @Override @Transactional
    public void updateAccount(Long id,Long actor,AdminAccountUpdateRequest request) {
        if(request.roleId()!=null) {
            if(id.equals(actor))throw new BusinessException(409,"不能在账号管理中更改自己的角色");
            assignRole(id,request.roleId());
        } else if(adminMapper.lockById(id)==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        var update=new Admin();update.setId(id);update.setRealName(request.realName()==null?"":request.realName());adminMapper.update(update);
    }

    @Override @Transactional
    public void setStatus(Long id,Long actor,Integer status) {
        if(status==null||(status!=0&&status!=1))throw new BusinessException(ErrorCode.BAD_REQUEST);
        if(id.equals(actor))throw new BusinessException(409,"不能停用自己的管理账号");
        roleMapper.lockSuperAdmins();var admin=adminMapper.lockById(id);
        if(admin==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        if(status==0)protectLastSuper(admin);
        if(!status.equals(admin.getStatus())) {
            if(status==0)sessions.revokeAll("ADMIN:"+id);
            var update=new Admin();update.setId(id);update.setStatus(status);adminMapper.update(update);
        }
    }

    @Override @Transactional
    public void resetPassword(Long id,Long actor,String password) {
        if(id.equals(actor))throw new BusinessException(409,"请使用个人密码设置修改自己的密码");
        if(adminMapper.lockById(id)==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        adminMapper.updatePassword(id,passwords.encode(password));
    }
}
