package com.paperless.local.controller;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.paperless.local.entity.Document;
import com.paperless.local.entity.DocumentCategory;
import com.paperless.local.entity.DocumentTagRel;
import com.paperless.local.entity.User;
import com.paperless.local.model.LoginUser;
import com.paperless.local.service.DocumentCategoryService;
import com.paperless.local.service.DocumentService;
import com.paperless.local.service.DocumentTagRelService;
import com.paperless.local.service.DocumentTagService;
import com.paperless.local.service.UserService;

@Controller
public class HomeController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DocumentService documentService;
    private final DocumentCategoryService categoryService;
    private final DocumentTagService tagService;
    private final DocumentTagRelService tagRelService;
    private final UserService userService;

    public HomeController(
            DocumentService documentService,
            DocumentCategoryService categoryService,
            DocumentTagService tagService,
            DocumentTagRelService tagRelService,
            UserService userService
    ) {
        this.documentService = documentService;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.tagRelService = tagRelService;
        this.userService = userService;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@RequestParam(name = "denied", required = false) String denied, HttpServletRequest request, Model model) {
        prepareApp(model, "dashboard", "工作台", currentUser(request));
        if (denied != null) {
            model.addAttribute("warning", "当前账号无权访问该管理页面");
        }
        return "app";
    }

    @GetMapping("/logs")
    public String logs(Model model) {
        prepareApp(model, "logs", "操作日志");
        return "app";
    }

    public void prepareApp(Model model, String activePage, String pageTitle) {
        prepareApp(model, activePage, pageTitle, null);
    }

    public void prepareApp(Model model, String activePage, String pageTitle, LoginUser currentUser) {
        List<LookupView> categories = categoryViews();
        List<LookupView> tags = tagViews();
        List<DocumentView> documents = documentViews(currentUser);
        long storageTotal = documents.stream()
                .map(DocumentView::fileSize)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        model.addAttribute("activePage", activePage);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("documents", documents);
        model.addAttribute("categories", categories);
        model.addAttribute("tags", tags);
        model.addAttribute("documentTotal", documents.size());
        model.addAttribute("inboxTotal", documents.size());
        model.addAttribute("storageTotal", formatBytes(storageTotal));
        model.addAttribute("categoryTotal", categories.size());
        model.addAttribute("tagTotal", tags.size());
    }

    public void prepareManagePage(Model model, String activePage, String pageTitle, Long editId) {
        prepareApp(model, activePage, pageTitle);
        List<LookupView> managementItems = "categories".equals(activePage) ? categoryViews() : tagViews();
        model.addAttribute("managementItems", managementItems);
        if (editId != null) {
            managementItems.stream()
                    .filter(item -> item.id().equals(editId))
                    .findFirst()
                    .ifPresent(item -> model.addAttribute("editItem", item));
        }
    }

    public List<LookupView> categoryViews() {
        return categoryService.list().stream()
                .map(category -> new LookupView(
                        category.getId(),
                        category.getName(),
                        category.getDescription(),
                        documentService.count(Wrappers.<Document>lambdaQuery().eq(Document::getCategoryId, category.getId()))
                ))
                .toList();
    }

    public List<LookupView> tagViews() {
        return tagService.list().stream()
                .map(tag -> new LookupView(
                        tag.getId(),
                        tag.getName(),
                        tag.getColor(),
                        tagRelService.count(Wrappers.<DocumentTagRel>lambdaQuery().eq(DocumentTagRel::getTagId, tag.getId()))
                ))
                .toList();
    }

    public DocumentView documentView(Document document) {
        DocumentCategory category = document.getCategoryId() == null ? null : categoryService.getById(document.getCategoryId());
        List<DocumentTagRel> relations = tagRelService.list(Wrappers.<DocumentTagRel>lambdaQuery()
                .eq(DocumentTagRel::getDocumentId, document.getId()));
        List<Long> tagIds = relations.stream().map(DocumentTagRel::getTagId).toList();
        List<TagView> tagViews = tagIds.stream()
                .map(tagService::getById)
                .filter(Objects::nonNull)
                .map(tag -> new TagView(tag.getId(), tag.getName(), tag.getColor()))
                .toList();
        String created = document.getUploadedAt() == null ? "" : document.getUploadedAt().format(DATE_FORMATTER);
        String updated = document.getUpdatedAt() == null ? created : document.getUpdatedAt().format(DATE_FORMATTER);
        return new DocumentView(
                document.getId(),
                document.getId() == null ? 0L : document.getId(),
                ownerName(document.getUploadUserId()),
                document.getTitle(),
                tagViews,
                ownerName(document.getUploadUserId()),
                category == null ? "" : category.getName(),
                category == null ? "" : category.getName(),
                created,
                updated,
                document.getFileType(),
                document.getDescription(),
                document.getOriginalFilename(),
                document.getFileSize(),
                document.getCategoryId(),
                tagIds
        );
    }

    public List<DocumentView> documentViews() {
        return documentViews(null);
    }

    public List<DocumentView> documentViews(LoginUser currentUser) {
        return documentService.list(Wrappers.<Document>lambdaQuery()
                        .orderByDesc(Document::getUploadedAt)
                        .orderByDesc(Document::getId))
                .stream()
                .filter(document -> canAccess(document, currentUser))
                .map(this::documentView)
                .toList();
    }

    private boolean canAccess(Document document, LoginUser currentUser) {
        return currentUser == null || currentUser.isAdmin() || resolveUserId(currentUser).equals(document.getUploadUserId());
    }

    private Long resolveUserId(LoginUser currentUser) {
        User user = userService.getOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, currentUser.username()), false);
        if (user != null) {
            return user.getId();
        }
        return currentUser.isAdmin() ? 1L : 2L;
    }

    private LoginUser currentUser(HttpServletRequest request) {
        return (LoginUser) request.getAttribute("currentUser");
    }

    private String ownerName(Long uploadUserId) {
        if (uploadUserId == null) {
            return "";
        }
        User user = userService.getById(uploadUserId);
        if (user != null && user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return "User " + uploadUserId;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format("%.1f MB", mb);
    }

    public record DocumentView(
            Long id,
            long asn,
            String correspondent,
            String title,
            List<TagView> tags,
            String owner,
            String documentType,
            String categoryName,
            String created,
            String added,
            String fileType,
            String description,
            String originalFilename,
            Long fileSize,
            Long categoryId,
            List<Long> tagIds
    ) {
    }

    public record LookupView(Long id, String name, String description, long count) {
    }

    public record TagView(Long id, String name, String color) {
    }
}
