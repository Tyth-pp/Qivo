package com.qivo.service;

import com.qivo.entity.Admin;
import com.qivo.vo.PageVO;
import com.qivo.vo.AdminAccountVO;
import com.qivo.dto.AdminAccountRequest;
import com.qivo.dto.AdminAccountUpdateRequest;
import java.util.Map;

public interface AdminService {
    Admin getById(Long id);

    void updateProfile(Long id, String realName, String avatar);

    Map<String, Object> login(String username, String password);

    void updatePassword(Long adminId, String oldPassword, String newPassword);

    PageVO<Admin> list(int page, int pageSize);

    void add(Admin admin);

    void assignRole(Long adminId, Long roleId);
    PageVO<AdminAccountVO> accounts(int page,int pageSize,String keyword,Integer status);
    void createAccount(AdminAccountRequest request);
    void updateAccount(Long id,Long actor,AdminAccountUpdateRequest request);
    void setStatus(Long id,Long actor,Integer status);
    void resetPassword(Long id,Long actor,String password);
}
