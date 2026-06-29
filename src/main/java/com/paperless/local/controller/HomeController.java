package com.paperless.local.controller;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.paperless.local.entity.Document;
import com.paperless.local.entity.DocumentCategory;
import com.paperless.local.entity.DocumentTag;
import com.paperless.local.entity.DocumentTagRel;
import com.paperless.local.service.DocumentCategoryService;
import com.paperless.local.service.DocumentService;
import com.paperless.local.service.DocumentTagRelService;
import com.paperless.local.service.DocumentTagService;

@Controller
public class HomeController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DocumentService documentService;
    private final DocumentCategoryService categoryService;
    private final DocumentTagService tagService;
    private final DocumentTagRelService tagRelService;

    public HomeController(
            DocumentService documentService,
            DocumentCategoryService categoryService,
            DocumentTagService tagService,
            DocumentTagRelService tagRelService
    ) {
        this.documentService = documentService;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.tagRelService = tagRelService;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@RequestParam(name = "denied", required = false) String denied, Model model) {
        prepareApp(model, "dashboard", "Dashboard");
        if (denied != null) {
            model.addAttribute("warning", "当前账号无权访问该管理页面");
        }
        return "app";
    }

    @GetMapping("/users")
    public String users(Model model) {
        prepareApp(model, "users", "Users & Groups");
        return "app";
    }

    @GetMapping("/logs")
    public String logs(Model model) {
        prepareApp(model, "logs", "Logs");
        return "app";
    }

    public void prepareApp(Model model, String activePage, String pageTitle) {
        List<LookupView> categories = categoryViews();
        List<LookupView> tags = tagViews();
        List<DocumentView> documents = documentViews();
        model.addAttribute("activePage", activePage);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("documents", documents);
        model.addAttribute("categories", categories);
        model.addAttribute("tags", tags);
        model.addAttribute("documentTotal", documents.size());
        model.addAttribute("inboxTotal", 5);
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
        return documentService.list(Wrappers.<Document>lambdaQuery()
                        .orderByDesc(Document::getUploadedAt)
                        .orderByDesc(Document::getId))
                .stream()
                .map(this::documentView)
                .toList();
    }

    private String ownerName(Long uploadUserId) {
        if (uploadUserId == null) {
            return "";
        }
        if (uploadUserId == 1L) {
            return "管理员";
        }
        if (uploadUserId == 2L) {
            return "普通用户";
        }
        return "User " + uploadUserId;
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
