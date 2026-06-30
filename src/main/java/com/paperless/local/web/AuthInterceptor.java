package com.paperless.local.web;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.paperless.local.model.LoginUser;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Set<String> ADMIN_PATHS = Set.of(
            "/categories",
            "/tags",
            "/users",
            "/logs",
            "/reviews",
            "/dev"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        HttpSession session = request.getSession(false);
        LoginUser loginUser = session == null ? null : (LoginUser) session.getAttribute(SessionKeys.LOGIN_USER);

        if (loginUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        if (requiresAdmin(request.getRequestURI(), request.getContextPath()) && !loginUser.isAdmin()) {
            response.sendRedirect(request.getContextPath() + "/dashboard?denied");
            return false;
        }

        request.setAttribute("currentUser", loginUser);
        return true;
    }

    private boolean requiresAdmin(String requestUri, String contextPath) {
        String path = contextPath == null || contextPath.isBlank()
                ? requestUri
                : requestUri.substring(contextPath.length());
        return ADMIN_PATHS.stream().anyMatch(path::startsWith);
    }
}
