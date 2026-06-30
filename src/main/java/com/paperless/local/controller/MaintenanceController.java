package com.paperless.local.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.paperless.local.entity.Document;
import com.paperless.local.entity.User;
import com.paperless.local.model.LoginUser;
import com.paperless.local.service.DocumentService;
import com.paperless.local.service.UserService;

@Controller
public class MaintenanceController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DocumentService documentService;
    private final UserService userService;
    private final HomeController homeController;

    public MaintenanceController(
            DocumentService documentService,
            UserService userService,
            HomeController homeController
    ) {
        this.documentService = documentService;
        this.userService = userService;
        this.homeController = homeController;
    }

    @GetMapping("/uploaders")
    public String uploaders(HttpServletRequest request, Model model) {
        LoginUser currentUser = currentUser(request);
        List<Document> visibleDocuments = visibleDocuments(currentUser);
        List<UploaderView> uploaders = visibleDocuments.stream()
                .collect(LinkedHashMap<Long, UploaderAccumulator>::new, this::accumulateUploader, Map::putAll)
                .values()
                .stream()
                .map(UploaderAccumulator::toView)
                .sorted(Comparator.comparing(UploaderView::latestUploadedAtValue, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        homeController.prepareApp(model, "uploaders", "上传人", currentUser);
        model.addAttribute("uploaders", uploaders);
        model.addAttribute("uploaderDocumentTotal", visibleDocuments.size());
        return "app";
    }

    @GetMapping("/uploaders/{userId}/documents")
    public String uploaderDocuments(@PathVariable Long userId, HttpServletRequest request, Model model, RedirectAttributes redirectAttributes) {
        LoginUser currentUser = currentUser(request);
        List<Document> documents = visibleDocuments(currentUser).stream()
                .filter(document -> userId.equals(document.getUploadUserId()))
                .toList();
        if (documents.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "该上传人暂无可查看文档");
            return "redirect:/uploaders";
        }

        User uploader = userService.getById(userId);
        homeController.prepareApp(model, "uploaders", "上传人文档", currentUser);
        model.addAttribute("selectedUploader", uploaderView(userId, documents));
        model.addAttribute("uploaderDocuments", documents.stream().map(homeController::documentView).toList());
        model.addAttribute("selectedUploaderName", uploader == null ? "User " + userId : uploader.getNickname());
        return "app";
    }

    private List<Document> visibleDocuments(LoginUser currentUser) {
        return documentService.list(Wrappers.<Document>lambdaQuery()
                        .orderByDesc(Document::getUploadedAt)
                        .orderByDesc(Document::getId))
                .stream()
                .filter(document -> canAccess(document, currentUser))
                .toList();
    }

    private void accumulateUploader(Map<Long, UploaderAccumulator> accumulatorMap, Document document) {
        Long userId = document.getUploadUserId();
        accumulatorMap.computeIfAbsent(userId, this::newUploaderAccumulator).add(document);
    }

    private UploaderAccumulator newUploaderAccumulator(Long userId) {
        User user = userId == null ? null : userService.getById(userId);
        return new UploaderAccumulator(
                userId,
                user == null ? "User " + userId : user.getUsername(),
                user == null ? "User " + userId : user.getNickname(),
                user == null ? "" : user.getAvatarUrl()
        );
    }

    private UploaderView uploaderView(Long userId, List<Document> documents) {
        UploaderAccumulator accumulator = newUploaderAccumulator(userId);
        documents.forEach(accumulator::add);
        return accumulator.toView();
    }

    private boolean canAccess(Document document, LoginUser currentUser) {
        return currentUser.isAdmin() || currentUser.id().equals(document.getUploadUserId());
    }

    private LoginUser currentUser(HttpServletRequest request) {
        return (LoginUser) request.getAttribute("currentUser");
    }

    private String formatDate(LocalDateTime time) {
        return time == null ? "" : time.format(DATE_TIME_FORMATTER);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        return String.format("%.1f MB", kb / 1024.0);
    }

    private class UploaderAccumulator {
        private final Long userId;
        private final String username;
        private final String nickname;
        private final String avatarUrl;
        private long documentCount;
        private long totalSize;
        private LocalDateTime latestUploadedAt;

        UploaderAccumulator(Long userId, String username, String nickname, String avatarUrl) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.avatarUrl = avatarUrl;
        }

        void add(Document document) {
            documentCount++;
            totalSize += Optional.ofNullable(document.getFileSize()).orElse(0L);
            if (document.getUploadedAt() != null && (latestUploadedAt == null || document.getUploadedAt().isAfter(latestUploadedAt))) {
                latestUploadedAt = document.getUploadedAt();
            }
        }

        UploaderView toView() {
            return new UploaderView(
                    userId,
                    username,
                    nickname,
                    avatarUrl,
                    documentCount,
                    formatBytes(totalSize),
                    formatDate(latestUploadedAt),
                    latestUploadedAt
            );
        }
    }

    public record UploaderView(
            Long userId,
            String username,
            String nickname,
            String avatarUrl,
            long documentCount,
            String totalSize,
            String latestUploadedAt,
            LocalDateTime latestUploadedAtValue
    ) {
    }

}
