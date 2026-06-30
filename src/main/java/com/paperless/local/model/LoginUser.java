package com.paperless.local.model;

public record LoginUser(Long id, String username, String displayName, String role, String avatarUrl) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
