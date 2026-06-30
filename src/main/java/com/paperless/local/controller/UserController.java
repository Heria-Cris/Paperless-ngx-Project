package com.paperless.local.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.paperless.local.entity.User;
import com.paperless.local.model.LoginUser;
import com.paperless.local.service.AuthService;
import com.paperless.local.service.DocumentService;
import com.paperless.local.service.UserService;
import com.paperless.local.web.SessionKeys;

@Controller
public class UserController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> AVATAR_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "svg");

    private final UserService userService;
    private final AuthService authService;
    private final DocumentService documentService;
    private final HomeController homeController;
    private final Path avatarRoot;

    public UserController(
            UserService userService,
            AuthService authService,
            DocumentService documentService,
            HomeController homeController,
            @Value("${app.upload-dir:uploads}") String uploadDir
    ) {
        this.userService = userService;
        this.authService = authService;
        this.documentService = documentService;
        this.homeController = homeController;
        this.avatarRoot = Paths.get(uploadDir).resolve("avatars").toAbsolutePath().normalize();
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("pageTitle", "注册账号");
        return "login";
    }

    @PostMapping("/register")
    public String doRegister(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam String nickname,
            @RequestParam(required = false) String email,
            RedirectAttributes redirectAttributes
    ) {
        String trimmedUsername = trim(username);
        String trimmedNickname = trim(nickname);
        if (isBlank(trimmedUsername) || isBlank(password) || isBlank(confirmPassword) || isBlank(trimmedNickname)) {
            redirectAttributes.addFlashAttribute("error", "账号、昵称和密码不能为空");
            return "redirect:/register";
        }
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "两次输入的密码不一致");
            return "redirect:/register";
        }
        if (password.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "密码长度至少 6 位");
            return "redirect:/register";
        }
        if (userService.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, trimmedUsername))) {
            redirectAttributes.addFlashAttribute("error", "账号已存在");
            return "redirect:/register";
        }

        User user = new User();
        user.setUsername(trimmedUsername);
        user.setPasswordHash(authService.hashPassword(password));
        user.setNickname(trimmedNickname);
        user.setAvatarUrl(defaultAvatar(trimmedNickname));
        user.setEmail(trim(email));
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userService.save(user);

        redirectAttributes.addFlashAttribute("success", "注册成功，请登录");
        return "redirect:/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        model.addAttribute("pageTitle", "忘记密码");
        return "login";
    }

    @PostMapping("/forgot-password")
    public String resetForgottenPassword(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(username) || isBlank(email) || isBlank(newPassword) || isBlank(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "账号、邮箱和新密码不能为空");
            return "redirect:/forgot-password";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "两次输入的新密码不一致");
            return "redirect:/forgot-password";
        }
        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "新密码长度至少 6 位");
            return "redirect:/forgot-password";
        }

        User user = userService.getOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, trim(username))
                .eq(User::getEmail, trim(email)), false);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            redirectAttributes.addFlashAttribute("error", "账号或邮箱不匹配，或账号已被禁用");
            return "redirect:/forgot-password";
        }
        user.setPasswordHash(authService.hashPassword(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);
        redirectAttributes.addFlashAttribute("success", "密码已重置，请使用新密码登录");
        return "redirect:/login";
    }

    @GetMapping("/profile")
    public String profile(HttpServletRequest request, Model model) {
        LoginUser currentUser = currentUser(request);
        homeController.prepareApp(model, "profile", "个人中心", currentUser);
        model.addAttribute("profileUser", userService.getById(currentUser.id()));
        return "app";
    }

    @PostMapping("/profile")
    public String updateProfile(
            @RequestParam String nickname,
            @RequestParam(required = false) String avatarUrl,
            @RequestParam(required = false) MultipartFile avatarFile,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String bio,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        LoginUser currentUser = currentUser(request);
        User user = userService.getById(currentUser.id());
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "当前用户不存在");
            return "redirect:/profile";
        }
        if (isBlank(nickname)) {
            redirectAttributes.addFlashAttribute("error", "昵称不能为空");
            return "redirect:/profile";
        }
        user.setNickname(trim(nickname));
        try {
            String uploadedAvatarUrl = storeAvatar(avatarFile, user.getId());
            if (!isBlank(uploadedAvatarUrl)) {
                user.setAvatarUrl(uploadedAvatarUrl);
            } else {
                user.setAvatarUrl(isBlank(avatarUrl) ? defaultAvatar(nickname) : trim(avatarUrl));
            }
        } catch (IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/profile";
        }
        user.setEmail(trim(email));
        user.setPhone(trim(phone));
        user.setBio(trim(bio));
        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);
        request.getSession().setAttribute(SessionKeys.LOGIN_USER, authService.toLoginUser(user));
        redirectAttributes.addFlashAttribute("success", "个人信息已更新");
        return "redirect:/profile";
    }

    @GetMapping("/avatars/{userFolder}/{filename:.+}")
    public ResponseEntity<Resource> avatar(@PathVariable String userFolder, @PathVariable String filename) throws IOException {
        Path filePath = avatarRoot.resolve(userFolder).resolve(filename).normalize();
        if (!filePath.startsWith(avatarRoot) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(filePath.toUri());
        ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
    }

    @PostMapping("/profile/password")
    public String updatePassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        LoginUser currentUser = currentUser(request);
        User user = userService.getById(currentUser.id());
        if (user == null || !authService.matches(oldPassword, user.getPasswordHash(), user.getUsername())) {
            redirectAttributes.addFlashAttribute("error", "原密码不正确");
            return "redirect:/profile";
        }
        if (isBlank(newPassword) || newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "新密码长度至少 6 位");
            return "redirect:/profile";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "两次输入的新密码不一致");
            return "redirect:/profile";
        }
        user.setPasswordHash(authService.hashPassword(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);
        redirectAttributes.addFlashAttribute("success", "密码已更新");
        return "redirect:/profile";
    }

    @GetMapping("/users")
    public String users(@RequestParam(name = "editId", required = false) Long editId, HttpServletRequest request, Model model) {
        homeController.prepareApp(model, "users", "用户管理", currentUser(request));
        List<UserView> users = userService.list(Wrappers.<User>lambdaQuery()
                        .eq(User::getRole, "USER")
                        .orderByDesc(User::getCreatedAt)
                        .orderByDesc(User::getId))
                .stream()
                .map(this::userView)
                .toList();
        model.addAttribute("users", users);
        if (editId != null) {
            Optional<UserView> editUser = users.stream()
                    .filter(user -> user.id().equals(editId))
                    .findFirst();
            editUser.ifPresent(user -> model.addAttribute("editUser", user));
        }
        return "app";
    }

    @PostMapping("/users")
    public String createUser(
            @RequestParam String username,
            @RequestParam String nickname,
            @RequestParam String password,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            RedirectAttributes redirectAttributes
    ) {
        String trimmedUsername = trim(username);
        if (isBlank(trimmedUsername) || isBlank(nickname) || isBlank(password)) {
            redirectAttributes.addFlashAttribute("error", "账号、昵称和初始密码不能为空");
            return "redirect:/users";
        }
        if (password.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "初始密码长度至少 6 位");
            return "redirect:/users";
        }
        if (userService.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, trimmedUsername))) {
            redirectAttributes.addFlashAttribute("error", "账号已存在");
            return "redirect:/users";
        }
        User user = new User();
        user.setUsername(trimmedUsername);
        user.setNickname(trim(nickname));
        user.setPasswordHash(authService.hashPassword(password));
        user.setAvatarUrl(defaultAvatar(nickname));
        user.setEmail(trim(email));
        user.setPhone(trim(phone));
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userService.save(user);
        redirectAttributes.addFlashAttribute("success", "用户创建成功");
        return "redirect:/users";
    }

    @PostMapping("/users/{id}/update")
    public String updateUser(
            @PathVariable Long id,
            @RequestParam String nickname,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String bio,
            RedirectAttributes redirectAttributes
    ) {
        Optional<User> existing = normalUser(id);
        if (existing.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "普通用户不存在");
            return "redirect:/users";
        }
        if (isBlank(nickname)) {
            redirectAttributes.addFlashAttribute("error", "昵称不能为空");
            return "redirect:/users?editId=" + id;
        }
        User user = existing.get();
        user.setNickname(trim(nickname));
        user.setEmail(trim(email));
        user.setPhone(trim(phone));
        user.setBio(trim(bio));
        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);
        redirectAttributes.addFlashAttribute("success", "用户信息已更新");
        return "redirect:/users";
    }

    @PostMapping("/users/{id}/password")
    public String resetUserPassword(@PathVariable Long id, @RequestParam String password, RedirectAttributes redirectAttributes) {
        Optional<User> existing = normalUser(id);
        if (existing.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "普通用户不存在");
            return "redirect:/users";
        }
        if (isBlank(password) || password.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "新密码长度至少 6 位");
            return "redirect:/users?editId=" + id;
        }
        User user = existing.get();
        user.setPasswordHash(authService.hashPassword(password));
        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);
        redirectAttributes.addFlashAttribute("success", "用户密码已重置");
        return "redirect:/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Optional<User> existing = normalUser(id);
        if (existing.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "普通用户不存在");
            return "redirect:/users";
        }
        User user = existing.get();
        user.setStatus(user.getStatus() != null && user.getStatus() == 1 ? 0 : 1);
        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);
        redirectAttributes.addFlashAttribute("success", user.getStatus() == 1 ? "用户已启用" : "用户已禁用");
        return "redirect:/users";
    }

    @PostMapping("/users/{id}/delete")
    @Transactional
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Optional<User> existing = normalUser(id);
        if (existing.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "普通用户不存在");
            return "redirect:/users";
        }
        long documentCount = documentService.count(Wrappers.<com.paperless.local.entity.Document>lambdaQuery()
                .eq(com.paperless.local.entity.Document::getUploadUserId, id));
        if (documentCount > 0) {
            redirectAttributes.addFlashAttribute("error", "该用户已有文档，不能直接删除，可先禁用账号");
            return "redirect:/users";
        }
        boolean removed = userService.removeById(id);
        redirectAttributes.addFlashAttribute(removed ? "success" : "error", removed ? "用户已删除" : "用户删除失败");
        return "redirect:/users";
    }

    private Optional<User> normalUser(Long id) {
        User user = userService.getById(id);
        if (user == null || !"USER".equals(user.getRole())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    private UserView userView(User user) {
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getEmail(),
                user.getPhone(),
                user.getBio(),
                user.getStatus() != null && user.getStatus() == 1,
                user.getCreatedAt() == null ? "" : user.getCreatedAt().format(DATE_TIME_FORMATTER)
        );
    }

    private LoginUser currentUser(HttpServletRequest request) {
        return (LoginUser) request.getAttribute("currentUser");
    }

    private String defaultAvatar(String nickname) {
        return "/images/default-avatar.svg";
    }

    private String storeAvatar(MultipartFile avatarFile, Long userId) throws IOException {
        if (avatarFile == null || avatarFile.isEmpty()) {
            return "";
        }
        if (avatarFile.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("头像文件不能超过 2MB");
        }
        String originalFilename = avatarFile.getOriginalFilename() == null ? "" : avatarFile.getOriginalFilename();
        String extension = extensionOf(originalFilename);
        if (!AVATAR_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("头像仅支持 PNG、JPG、GIF、WEBP、SVG");
        }
        Path userDirectory = avatarRoot.resolve("user_" + userId).normalize();
        if (!userDirectory.startsWith(avatarRoot)) {
            throw new IllegalArgumentException("头像存储路径不合法");
        }
        Files.createDirectories(userDirectory);
        String storedFilename = UUID.randomUUID() + "." + extension;
        Path target = userDirectory.resolve(storedFilename).normalize();
        if (!target.startsWith(avatarRoot)) {
            throw new IllegalArgumentException("头像存储路径不合法");
        }
        avatarFile.transferTo(target);
        return "/avatars/user_" + userId + "/" + storedFilename;
    }

    private String extensionOf(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("头像文件必须包含扩展名");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    public record UserView(
            Long id,
            String username,
            String nickname,
            String avatarUrl,
            String email,
            String phone,
            String bio,
            boolean enabled,
            String createdAt
    ) {
    }
}
