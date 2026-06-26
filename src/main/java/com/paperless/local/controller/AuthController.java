package com.paperless.local.controller;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.paperless.local.model.LoginUser;
import com.paperless.local.service.AuthService;
import com.paperless.local.web.SessionKeys;

@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("pageTitle", "登录");
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(
            @RequestParam String username,
            @RequestParam String password,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        return authService.login(username, password)
                .map(user -> loginSuccess(session, user))
                .orElseGet(() -> loginFailure(redirectAttributes, username));
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login?logout";
    }

    private String loginSuccess(HttpSession session, LoginUser user) {
        session.setAttribute(SessionKeys.LOGIN_USER, user);
        return "redirect:/dashboard";
    }

    private String loginFailure(RedirectAttributes redirectAttributes, String username) {
        redirectAttributes.addFlashAttribute("error", "用户名或密码错误");
        redirectAttributes.addFlashAttribute("username", username);
        return "redirect:/login";
    }
}
