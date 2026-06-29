package com.paperless.local.controller;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.paperless.local.entity.Document;
import com.paperless.local.entity.DocumentTag;
import com.paperless.local.entity.DocumentTagRel;
import com.paperless.local.entity.User;
import com.paperless.local.model.LoginUser;
import com.paperless.local.service.DocumentService;
import com.paperless.local.service.DocumentTagRelService;
import com.paperless.local.service.DocumentTagService;
import com.paperless.local.service.UserService;

@Controller
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentTagService tagService;
    private final DocumentTagRelService tagRelService;
    private final UserService userService;
    private final HomeController homeController;

    public DocumentController(
            DocumentService documentService,
            DocumentTagService tagService,
            DocumentTagRelService tagRelService,
            UserService userService,
            HomeController homeController
    ) {
        this.documentService = documentService;
        this.tagService = tagService;
        this.tagRelService = tagRelService;
        this.userService = userService;
        this.homeController = homeController;
    }

    @GetMapping("/documents")
    public String documents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            HttpServletRequest request,
            Model model
    ) {
        homeController.prepareApp(model, "documents", "Documents");
        List<HomeController.DocumentView> filtered = filterDocuments(keyword, categoryId, tagId, currentUser(request));
        model.addAttribute("documents", filtered);
        model.addAttribute("documentTotal", filtered.size());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedTagId", tagId);
        return "app";
    }

    @GetMapping("/documents/upload")
    public String upload(Model model) {
        homeController.prepareApp(model, "upload", "Upload documents");
        return "app";
    }

    @PostMapping("/documents")
    @Transactional
    public String create(
            @RequestParam String title,
            @RequestParam String originalFilename,
            @RequestParam String fileType,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String description,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(title) || isBlank(originalFilename) || isBlank(fileType)) {
            redirectAttributes.addFlashAttribute("error", "文档标题、原始文件名和文件类型不能为空");
            return "redirect:/documents/upload";
        }

        Document document = new Document();
        document.setTitle(title.trim());
        document.setOriginalFilename(originalFilename.trim());
        document.setFileType(fileType.trim().toUpperCase());
        document.setCategoryId(categoryId);
        document.setUploadUserId(resolveUserId(currentUser(request)));
        document.setDescription(description);
        document.setFileSize(0L);
        document.setStoredFilename("metadata-only-" + System.currentTimeMillis() + "-" + originalFilename.trim());
        document.setStoragePath("uploads/metadata-only/" + document.getStoredFilename());
        document.setDeleted(0);
        document.setUploadedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentService.save(document);
        replaceTags(document.getId(), safeTagIds(tagIds));

        redirectAttributes.addFlashAttribute("success", "文档元数据创建成功");
        return "redirect:/documents/" + document.getId();
    }

    @GetMapping("/documents/{id}")
    public String documentDetail(@PathVariable Long id, HttpServletRequest request, Model model, RedirectAttributes redirectAttributes) {
        Optional<Document> document = findAccessibleDocument(id, currentUser(request));
        if (document.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "文档不存在或无权访问");
            return "redirect:/documents";
        }
        homeController.prepareApp(model, "document-detail", "Document detail");
        model.addAttribute("selectedDocument", homeController.documentView(document.get()));
        return "app";
    }

    @GetMapping("/documents/{id}/edit")
    public String documentEdit(@PathVariable Long id, HttpServletRequest request, Model model, RedirectAttributes redirectAttributes) {
        Optional<Document> document = findAccessibleDocument(id, currentUser(request));
        if (document.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "文档不存在或无权访问");
            return "redirect:/documents";
        }
        homeController.prepareApp(model, "document-edit", "Edit document");
        model.addAttribute("selectedDocument", homeController.documentView(document.get()));
        return "app";
    }

    @PostMapping("/documents/{id}/update")
    @Transactional
    public String update(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam String originalFilename,
            @RequestParam String fileType,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String description,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Document> existing = findAccessibleDocument(id, currentUser(request));
        if (existing.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "文档不存在或无权访问");
            return "redirect:/documents";
        }
        if (isBlank(title) || isBlank(originalFilename) || isBlank(fileType)) {
            redirectAttributes.addFlashAttribute("error", "文档标题、原始文件名和文件类型不能为空");
            return "redirect:/documents/" + id + "/edit";
        }

        Document document = existing.get();
        document.setTitle(title.trim());
        document.setOriginalFilename(originalFilename.trim());
        document.setFileType(fileType.trim().toUpperCase());
        document.setCategoryId(categoryId);
        document.setDescription(description);
        document.setUpdatedAt(LocalDateTime.now());
        documentService.updateById(document);
        replaceTags(id, safeTagIds(tagIds));

        redirectAttributes.addFlashAttribute("success", "文档元数据更新成功");
        return "redirect:/documents/" + id;
    }

    @PostMapping("/documents/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Optional<Document> document = findAccessibleDocument(id, currentUser(request));
        if (document.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "文档不存在或无权访问");
            return "redirect:/documents";
        }
        documentService.removeById(id);
        redirectAttributes.addFlashAttribute("success", "文档删除成功");
        return "redirect:/documents";
    }

    private List<HomeController.DocumentView> filterDocuments(String keyword, Long categoryId, Long tagId, LoginUser currentUser) {
        List<Long> tagDocumentIds = tagId == null
                ? Collections.emptyList()
                : tagRelService.list(Wrappers.<DocumentTagRel>lambdaQuery().eq(DocumentTagRel::getTagId, tagId))
                .stream()
                .map(DocumentTagRel::getDocumentId)
                .toList();

        return documentService.list(Wrappers.<Document>lambdaQuery().orderByDesc(Document::getUploadedAt).orderByDesc(Document::getId))
                .stream()
                .filter(document -> canAccess(document, currentUser))
                .filter(document -> categoryId == null || categoryId.equals(document.getCategoryId()))
                .filter(document -> tagId == null || tagDocumentIds.contains(document.getId()))
                .filter(document -> matchesKeyword(document, keyword))
                .map(homeController::documentView)
                .toList();
    }

    private boolean matchesKeyword(Document document, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(document.getTitle(), normalized)
                || contains(document.getOriginalFilename(), normalized)
                || contains(document.getDescription(), normalized);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private Optional<Document> findAccessibleDocument(Long id, LoginUser currentUser) {
        Document document = documentService.getById(id);
        if (document == null || !canAccess(document, currentUser)) {
            return Optional.empty();
        }
        return Optional.of(document);
    }

    private boolean canAccess(Document document, LoginUser currentUser) {
        return currentUser.isAdmin() || resolveUserId(currentUser).equals(document.getUploadUserId());
    }

    private LoginUser currentUser(HttpServletRequest request) {
        return (LoginUser) request.getAttribute("currentUser");
    }

    private Long resolveUserId(LoginUser currentUser) {
        User user = userService.getOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, currentUser.username()), false);
        if (user != null) {
            return user.getId();
        }
        return currentUser.isAdmin() ? 1L : 2L;
    }

    private void replaceTags(Long documentId, List<Long> tagIds) {
        tagRelService.remove(Wrappers.<DocumentTagRel>lambdaQuery().eq(DocumentTagRel::getDocumentId, documentId));
        for (Long tagId : tagIds) {
            DocumentTag tag = tagService.getById(tagId);
            if (tag == null) {
                continue;
            }
            DocumentTagRel relation = new DocumentTagRel();
            relation.setDocumentId(documentId);
            relation.setTagId(tagId);
            relation.setCreatedAt(LocalDateTime.now());
            tagRelService.save(relation);
        }
    }

    private List<Long> safeTagIds(List<Long> tagIds) {
        return tagIds == null ? Collections.emptyList() : tagIds;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }
}
