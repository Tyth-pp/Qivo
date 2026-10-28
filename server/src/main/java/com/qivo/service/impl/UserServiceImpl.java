package com.qivo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qivo.constant.SecurityConstant;
import com.qivo.utils.AvatarFiles;
import com.qivo.entity.Address;
import com.qivo.entity.User;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.AddressMapper;
import com.qivo.mapper.UserMapper;
import com.qivo.persistence.PageRequests;
import com.qivo.result.ErrorCode;
import com.qivo.security.JwtTokenProvider;
import com.qivo.security.Passwords;
import com.qivo.security.SessionRegistry;
import com.qivo.service.UserService;
import com.qivo.vo.PageVO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final AddressMapper addressMapper;
    private final JwtTokenProvider jwtTokens;
    private final Passwords passwords;
    private final SessionRegistry sessions;

    public UserServiceImpl(
            UserMapper userMapper,
            AddressMapper addressMapper,
            JwtTokenProvider jwtTokens,
            Passwords passwords,
            SessionRegistry sessions) {
        this.userMapper = userMapper;
        this.addressMapper = addressMapper;
        this.jwtTokens = jwtTokens;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public User getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public Map<String, Object> login(String username, String password) {
        User user = userMapper.selectByUsername(username);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        if (user.getStatus() == 0) throw new BusinessException(ErrorCode.USER_ACCOUNT_DISABLED);
        if (!passwords.matches(password, user.getPassword()))
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);

        String token =
                jwtTokens.generateToken(
                        user.getId(),
                        user.getUsername(),
                        SecurityConstant.DOMAIN_USER,
                        user.getPassword());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("email", user.getEmail());
        result.put("phone", user.getPhone());
        result.put("avatar", user.getAvatar());
        return result;
    }

    @Override
    @Transactional
    public void register(User user) {
        User exist = userMapper.selectByUsername(user.getUsername());
        if (exist != null) throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        user.setPassword(passwords.encode(user.getPassword()));
        user.setStatus(1);
        user.setScore(0);
        userMapper.insert(user);
    }

    @Override
    @Transactional
    public void updateProfile(User user) {
        User existing = userMapper.selectById(user.getId());
        if (existing == null) throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        if (user.getAvatar() != null && !user.getAvatar().equals(existing.getAvatar())) AvatarFiles.validatePath(user.getAvatar());
        User safe = new User();
        safe.setId(user.getId());
        safe.setEmail(user.getEmail());
        safe.setPhone(user.getPhone());
        safe.setAvatar(user.getAvatar());
        userMapper.update(safe);
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User account = userMapper.selectById(userId);
        if (account == null) throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        if (!passwords.matches(oldPassword, account.getPassword())) throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        if (passwords.matches(newPassword, account.getPassword())) throw new BusinessException(ErrorCode.BAD_REQUEST);
        if (userMapper.changePasswordIfUnchanged(userId, account.getPassword(), passwords.encode(newPassword)) != 1)
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    // 后台管理
    @Override
    public PageVO<User> page(int page, int pageSize, String keyword) {
        Page<User> pagination = PageRequests.of(page, pageSize);
        List<User> list = userMapper.selectPage(pagination, keyword);
        return PageVO.of(list, pagination.getTotal(), page, pageSize);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1))
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        if (userMapper.selectById(id) == null)
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        if (status == 0) sessions.revokeAll("USER:" + id);
        userMapper.updateStatus(id, status);
    }

    @Override
    @Transactional
    public void updateScore(Long id, Integer score) {
        if (userMapper.selectById(id) == null)
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        userMapper.updateScore(id, score);
    }

    // 地址管理
    @Override
    public List<Address> addressList(Long userId) {
        return addressMapper.selectByUserId(userId);
    }

    @Override
    public Address getAddressById(Long id) {
        return addressMapper.selectById(id);
    }

    @Override
    @Transactional
    public void addAddress(Address address) {
        if (address.getIsDefault() == 1) {
            addressMapper.clearDefaultByUserId(address.getUserId());
        }
        addressMapper.insert(address);
    }

    @Override
    @Transactional
    public void updateAddress(Address address) {
        Address existing = addressMapper.selectById(address.getId());
        if (existing == null || !existing.getUserId().equals(address.getUserId()))
            throw new BusinessException(ErrorCode.NOT_FOUND);
        if (address.getIsDefault() == 1) {
            addressMapper.clearDefaultByUserId(address.getUserId());
        }
        addressMapper.update(address);
    }

    @Override
    @Transactional
    public void deleteAddress(Long id, Long userId) {
        if (addressMapper.deleteById(id, userId) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND);
    }
}
