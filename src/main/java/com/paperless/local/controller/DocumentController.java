package com.paperless.local.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.paperless.local.entity.Document;
import com.paperless.local.entity.DocumentTag;
import com.paperless.local.entity.DocumentTagRel;
import com.paperless.local.entity.User;
import com.paperless.local.model.LoginUser;
import com.paperless.local.service.DocumentService;
import com.paperless.local.service.DocumentTagRelService;
import com.paperless.local.service.DocumentTagService;
import com.paperless.local.service.FileStorageService;
import com.paperless.local.service.UserService;

@Controller
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentTagService tagService;
    private final DocumentTagRelService tagRelService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final HomeController homeController;

    public DocumentController(
            DocumentService documentService,
            DocumentTagService tagService,
            DocumentTagRelService tagRelService,
            UserService userService,
            FileStorageService fileStorageService,
            HomeController homeController
    ) {
        this.documentService = documentService;
        this.tagService = tagService;
        this.tagRelService = tagRelService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.homeController = homeController;
    }

    @GetMapping("/documents")
    public String documents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request,
            Model model
    ) {
        homeController.prepareApp(model, "documents", "Documents", currentUser(request));
        List<HomeController.DocumentView> filtered = filterDocuments(keyword, categoryId, tagId, currentUser(request));
        int pageSize = normalizePageSize(size);
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) pageSize));
        int currentPage = Math.min(Math.max(1, page), totalPages);
        int fromIndex = Math.min((currentPage - 1) * pageSize, filtered.size());
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
        List<HomeController.DocumentView> pagedDocuments = filtered.subList(fromIndex, toIndex);

        model.addAttribute("documents", pagedDocuments);
        model.addAttribute("documentTotal", filtered.size());
        model.addAttribute("resultTotal", filtered.size());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedTagId", tagId);
        model.addAttribute("page", currentPage);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPrevious", currentPage > 1);
        model.addAttribute("hasNext", currentPage < totalPages);
        model.addAttribute("previousPage", Math.max(1, currentPage - 1));
        model.addAttribute("nextPage", Math.min(totalPages, currentPage + 1));
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
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String description,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(title)) {
            redirectAttributes.addFlashAttribute("error", "文档标题不能为空");
            return "redirect:/documents/upload";
        }

        FileStorageService.StoredFile storedFile = null;
        try {
            Long userId = resolveUserId(currentUser(request));
            storedFile = fileStorageService.store(file, userId);

            Document document = new Document();
            document.setTitle(title.trim());
            document.setOriginalFilename(storedFile.originalFilename());
            document.setFileType(storedFile.fileType());
            document.setCategoryId(categoryId);
            document.setUploadUserId(userId);
            document.setDescription(description);
            document.setFileSize(storedFile.fileSize());
            document.setStoredFilename(storedFile.storedFilename());
            document.setStoragePath(storedFile.storagePath());
            document.setDeleted(0);
            document.setUploadedAt(LocalDateTime.now());
            document.setUpdatedAt(LocalDateTime.now());
            documentService.save(document);
            replaceTags(document.getId(), safeTagIds(tagIds));

            redirectAttributes.addFlashAttribute("success", "文件上传成功");
            return "redirect:/documents/" + document.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            if (storedFile != null) {
                fileStorageService.deleteIfExists(storedFile.storagePath());
            }
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            redirectAttributes.addFlashAttribute("error", "文件上传失败，请检查文件后重试");
        }
        return "redirect:/documents/upload";
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, HttpServletRequest request) throws Exception {
        Optional<Document> document = findAccessibleDocument(id, currentUser(request));
        if (document.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Document current = document.get();
        Path filePath = fileStorageService.resolve(current.getStoragePath());
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(current.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(Files.size(filePath)))
                .body(resource);
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

    private int normalizePageSize(Integer size) {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 50);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }
}
