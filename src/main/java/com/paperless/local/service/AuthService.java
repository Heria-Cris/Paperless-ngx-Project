package com.paperless.local.service;

import java.util.Optional;

import cn.hutool.crypto.digest.DigestUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import org.springframework.stereotype.Service;

import com.paperless.local.entity.User;
import com.paperless.local.model.LoginUser;

@Service
public class AuthService {

    private final UserService userService;

    public AuthService(UserService userService) {
        this.userService = userService;
    }

    public Optional<LoginUser> login(String username, String password) {
        if (isBlank(username) || isBlank(password)) {
            return Optional.empty();
        }
        User user = userService.getOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username.trim()), false);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return Optional.empty();
        }
        if (!matches(password, user.getPasswordHash(), user.getUsername())) {
            return Optional.empty();
        }
        return Optional.of(toLoginUser(user));
    }

    public String hashPassword(String password) {
        return DigestUtil.sha256Hex(password);
    }

    public boolean matches(String rawPassword, String storedPassword, String username) {
        if (storedPassword == null) {
            return false;
        }
        if (storedPassword.equals(hashPassword(rawPassword))) {
            return true;
        }
        return ("admin".equals(username) && "admin123".equals(rawPassword)
                && "TEMP_HASH_admin123_replace_later".equals(storedPassword))
                || ("user".equals(username) && "user123".equals(rawPassword)
                && "TEMP_HASH_user123_replace_later".equals(storedPassword));
    }

    public LoginUser toLoginUser(User user) {
        return new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole(),
                user.getAvatarUrl()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }
}
