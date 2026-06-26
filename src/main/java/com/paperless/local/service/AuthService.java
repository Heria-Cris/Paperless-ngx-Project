package com.paperless.local.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.paperless.local.model.LoginUser;

@Service
public class AuthService {

    private static final Map<String, DemoAccount> ACCOUNTS = Map.of(
            "admin", new DemoAccount("admin123", new LoginUser("admin", "管理员", "ADMIN")),
            "user", new DemoAccount("user123", new LoginUser("user", "普通用户", "USER"))
    );

    public Optional<LoginUser> login(String username, String password) {
        DemoAccount account = ACCOUNTS.get(username);
        if (account == null || !account.password().equals(password)) {
            return Optional.empty();
        }
        return Optional.of(account.user());
    }

    private record DemoAccount(String password, LoginUser user) {
    }
}
