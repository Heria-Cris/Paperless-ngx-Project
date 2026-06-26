package com.paperless.local.model;

public record LoginUser(String username, String displayName, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
