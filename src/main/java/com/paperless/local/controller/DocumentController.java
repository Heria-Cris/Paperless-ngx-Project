package com.paperless.local.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import com.paperless.local.service.DocumentTaskService;
import com.paperless.local.service.FileStorageService;
import com.paperless.local.service.OperationLogService;
import com.paperless.local.service.UserService;
import com.paperless.local.entity.DocumentTask;

@Controller
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentTagService tagService;
    private final DocumentTagRelService tagRelService;
    private final DocumentTaskService taskService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final OperationLogService operationLogService;
    private final HomeController homeController;

    public DocumentController(
            DocumentService documentService,
            DocumentTagService tagService,
            DocumentTagRelService tagRelService,
            DocumentTaskService taskService,
            UserService userService,
            FileStorageService fileStorageService,
            OperationLogService operationLogService,
            HomeController homeController
    ) {
        this.documentService = documentService;
        this.tagService = tagService;
        this.tagRelService = tagRelService;
        this.taskService = taskService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.operationLogService = operationLogService;
        this.homeController = homeController;
    }

    @GetMapping("/documents")
    public String documents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "all") String view,
            @RequestParam(defaultValue = "created_desc") String sort,
            @RequestParam(defaultValue = "list") String display,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request,
            Model model
    ) {
        homeController.prepareApp(model, "documents", "文档管理", currentUser(request));
        List<HomeController.DocumentView> filtered = filterDocuments(keyword, categoryId, tagId, view, sort, currentUser(request));
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
        model.addAttribute("selectedView", normalizeView(view));
        model.addAttribute("selectedSort", normalizeSort(sort));
        model.addAttribute("selectedDisplay", normalizeDisplay(display));
        model.addAttribute("documentPageTitle", "recent".equals(normalizeView(view)) ? "最近添加" : "文档管理");
        model.addAttribute("page", currentPage);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPrevious", currentPage > 1);
        model.addAttribute("hasNext", currentPage < totalPages);
        model.addAttribute("previousPage", Math.max(1, currentPage - 1));
        model.addAttribute("nextPage", Math.min(totalPages, currentPage + 1));
        operationLogService.record(currentUser(request), "LIST_DOCUMENTS", "DOCUMENT", null, request.getRemoteAddr(), "SUCCESS");
        return "app";
    }

    @GetMapping("/documents/upload")
    public String upload(Model model) {
        homeController.prepareApp(model, "upload", "上传文档");
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
            document.setReviewStatus(currentUser(request).isAdmin() ? "APPROVED" : "PENDING");
            document.setReviewComment("");
            if (currentUser(request).isAdmin()) {
                document.setReviewedBy(userId);
                document.setReviewedAt(LocalDateTime.now());
            }
            document.setUploadedAt(LocalDateTime.now());
            document.setUpdatedAt(LocalDateTime.now());
            documentService.save(document);
            replaceTags(document.getId(), safeTagIds(tagIds));
            createPendingTaskIfNeeded(document.getId(), userId, safeTagIds(tagIds));
            operationLogService.record(currentUser(request), "UPLOAD_DOCUMENT", "DOCUMENT", document.getId(), request.getRemoteAddr(), "SUCCESS");

            redirectAttributes.addFlashAttribute("success", currentUser(request).isAdmin() ? "文件上传成功" : "文件上传成功，等待管理员审查");
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

    @GetMapping("/reviews")
    public String reviews(HttpServletRequest request, Model model) {
        LoginUser currentUser = currentUser(request);
        homeController.prepareApp(model, "reviews", "文件审查", currentUser);
        List<HomeController.DocumentView> reviewDocuments = documentService.list(Wrappers.<Document>lambdaQuery()
                        .eq(Document::getDeleted, 0)
                        .eq(Document::getReviewStatus, "PENDING")
                        .orderByAsc(Document::getUploadedAt)
                        .orderByAsc(Document::getId))
                .stream()
                .map(homeController::documentView)
                .toList();
        model.addAttribute("reviewDocuments", reviewDocuments);
        model.addAttribute("reviewTotal", reviewDocuments.size());
        return "app";
    }

    @PostMapping("/reviews/{id}/approve")
    public String approveReview(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Document document = documentService.getById(id);
        if (document == null || deleted(document)) {
            redirectAttributes.addFlashAttribute("error", "文档不存在或已删除");
            return "redirect:/reviews";
        }
        document.setReviewStatus("APPROVED");
        document.setReviewComment("");
        document.setReviewedBy(currentUser(request).id());
        document.setReviewedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentService.updateById(document);
        operationLogService.record(currentUser(request), "APPROVE_DOCUMENT", "DOCUMENT", id, request.getRemoteAddr(), "SUCCESS");
        redirectAttributes.addFlashAttribute("success", "文档审查已通过");
        return "redirect:/reviews";
    }

    @PostMapping("/reviews/{id}/reject")
    public String rejectReview(
            @PathVariable Long id,
            @RequestParam(required = false) String reviewComment,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        Document document = documentService.getById(id);
        if (document == null || deleted(document)) {
            redirectAttributes.addFlashAttribute("error", "文档不存在或已删除");
            return "redirect:/reviews";
        }
        document.setReviewStatus("REJECTED");
        document.setReviewComment(isBlank(reviewComment) ? "管理员打回，请修改后重新提交" : reviewComment.trim());
        document.setReviewedBy(currentUser(request).id());
        document.setReviewedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentService.updateById(document);
        operationLogService.record(currentUser(request), "REJECT_DOCUMENT", "DOCUMENT", id, request.getRemoteAddr(), "SUCCESS");
        redirectAttributes.addFlashAttribute("success", "文档已打回");
        return "redirect:/reviews";
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
            operationLogService.record(currentUser(request), "DOWNLOAD_DOCUMENT", "DOCUMENT", id, request.getRemoteAddr(), "FAILED");
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        operationLogService.record(currentUser(request), "DOWNLOAD_DOCUMENT", "DOCUMENT", id, request.getRemoteAddr(), "SUCCESS");
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(current.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(Files.size(filePath)))
                .body(resource);
    }

    @GetMapping("/documents/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id, HttpServletRequest request) throws Exception {
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
        ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(current.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(previewMediaType(current.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
    }

    @GetMapping("/documents/{id}")
    public String documentDetail(@PathVariable Long id, HttpServletRequest request, Model model, RedirectAttributes redirectAttributes) {
        Optional<Document> document = findAccessibleDocument(id, currentUser(request));
        if (document.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "文档不存在或无权访问");
            return "redirect:/documents";
        }
        homeController.prepareApp(model, "document-detail", "文档详情", currentUser(request));
        HomeController.DocumentView selected = homeController.documentView(document.get());
        model.addAttribute("selectedDocument", selected);
        model.addAttribute("previewMode", previewMode(selected.fileType()));
        operationLogService.record(currentUser(request), "VIEW_DOCUMENT", "DOCUMENT", id, request.getRemoteAddr(), "SUCCESS");
        return "app";
    }

    @GetMapping("/documents/{id}/edit")
    public String documentEdit(@PathVariable Long id, HttpServletRequest request, Model model, RedirectAttributes redirectAttributes) {
        Optional<Document> document = findAccessibleDocument(id, currentUser(request));
        if (document.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "文档不存在或无权访问");
            return "redirect:/documents";
        }
        homeController.prepareApp(model, "document-edit", "编辑文档", currentUser(request));
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
        document.setReviewStatus(currentUser(request).isAdmin() ? "APPROVED" : "PENDING");
        document.setReviewComment(currentUser(request).isAdmin() ? "" : "资料已更新，等待管理员重新审查");
        if (currentUser(request).isAdmin()) {
            document.setReviewedBy(currentUser(request).id());
            document.setReviewedAt(LocalDateTime.now());
        }
        document.setUpdatedAt(LocalDateTime.now());
        documentService.updateById(document);
        replaceTags(id, safeTagIds(tagIds));
        operationLogService.record(currentUser(request), "UPDATE_DOCUMENT", "DOCUMENT", id, request.getRemoteAddr(), "SUCCESS");

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
        moveToRecycleBin(document.get());
        operationLogService.record(currentUser(request), "DELETE_DOCUMENT", "DOCUMENT", id, request.getRemoteAddr(), "SUCCESS");
        redirectAttributes.addFlashAttribute("success", "文档已移入回收站，可在 30 天内恢复");
        return "redirect:/documents";
    }

    @PostMapping("/documents/bulk-delete")
    @Transactional
    public String bulkDelete(
            @RequestParam(required = false) List<Long> documentIds,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        List<Long> ids = documentIds == null ? Collections.emptyList() : documentIds;
        int deletedCount = 0;
        for (Long id : ids) {
            Optional<Document> document = findAccessibleDocument(id, currentUser(request));
            if (document.isPresent()) {
                moveToRecycleBin(document.get());
                operationLogService.record(currentUser(request), "DELETE_DOCUMENT", "DOCUMENT", id, request.getRemoteAddr(), "SUCCESS");
                deletedCount++;
            }
        }
        redirectAttributes.addFlashAttribute("success", "已将 " + deletedCount + " 个文档移入回收站");
        return "redirect:/documents";
    }

    @GetMapping("/recycle-bin")
    public String recycleBin(HttpServletRequest request, Model model) {
        LoginUser currentUser = currentUser(request);
        homeController.prepareApp(model, "recycle-bin", "回收站", currentUser);
        List<RecycleDocumentView> recycleDocuments = documentService.list(Wrappers.<Document>lambdaQuery()
                        .eq(Document::getDeleted, 1)
                        .orderByDesc(Document::getDeletedAt)
                        .orderByDesc(Document::getId))
                .stream()
                .filter(document -> canAccessDeleted(document, currentUser))
                .map(document -> new RecycleDocumentView(homeController.documentView(document), recycleCountdown(document)))
                .toList();
        model.addAttribute("recycleDocuments", recycleDocuments);
        model.addAttribute("recycleTotal", recycleDocuments.size());
        return "app";
    }

    @PostMapping("/recycle-bin/{id}/restore")
    public String restoreDocument(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Optional<Document> document = findRecycleDocument(id, currentUser(request));
        if (document.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "回收站中未找到该文档");
            return "redirect:/recycle-bin";
        }
        restoreDocument(document.get());
        operationLogService.record(currentUser(request), "RESTORE_DOCUMENT", "DOCUMENT", id, request.getRemoteAddr(), "SUCCESS");
        redirectAttributes.addFlashAttribute("success", "文档已恢复");
        return "redirect:/recycle-bin";
    }

    @PostMapping("/recycle-bin/bulk-restore")
    public String bulkRestoreRecycleBin(
            @RequestParam(required = false) List<Long> documentIds,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        List<Long> ids = documentIds == null ? Collections.emptyList() : documentIds;
        int restoredCount = 0;
        for (Long id : ids) {
            Optional<Document> document = findRecycleDocument(id, currentUser(request));
            if (document.isPresent()) {
                restoreDocument(document.get());
                operationLogService.record(currentUser(request), "RESTORE_DOCUMENT", "DOCUMENT", id, request.getRemoteAddr(), "SUCCESS");
                restoredCount++;
            }
        }
        redirectAttributes.addFlashAttribute("success", "已恢复 " + restoredCount + " 个文档");
        return "redirect:/recycle-bin";
    }

    @PostMapping("/recycle-bin/restore-all")
    public String restoreAllRecycleBin(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        LoginUser currentUser = currentUser(request);
        List<Document> documents = documentService.list(Wrappers.<Document>lambdaQuery().eq(Document::getDeleted, 1))
                .stream()
                .filter(document -> canAccessDeleted(document, currentUser))
                .toList();
        for (Document document : documents) {
            restoreDocument(document);
            operationLogService.record(currentUser, "RESTORE_DOCUMENT", "DOCUMENT", document.getId(), request.getRemoteAddr(), "SUCCESS");
        }
        redirectAttributes.addFlashAttribute("success", "已一键恢复 " + documents.size() + " 个文档");
        return "redirect:/recycle-bin";
    }

    @PostMapping("/recycle-bin/{id}/purge")
    @Transactional
    public String purgeDocumentRoute(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Optional<Document> document = findRecycleDocument(id, currentUser(request));
        if (document.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "回收站中未找到该文档");
            return "redirect:/recycle-bin";
        }
        purgeDocument(document.get());
        operationLogService.record(currentUser(request), "PURGE_DOCUMENT", "DOCUMENT", id, request.getRemoteAddr(), "SUCCESS");
        redirectAttributes.addFlashAttribute("success", "文档已彻底删除");
        return "redirect:/recycle-bin";
    }

    @PostMapping("/recycle-bin/bulk-purge")
    @Transactional
    public String bulkPurgeRecycleBin(
            @RequestParam(required = false) List<Long> documentIds,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        List<Long> ids = documentIds == null ? Collections.emptyList() : documentIds;
        int purgedCount = 0;
        for (Long id : ids) {
            Optional<Document> document = findRecycleDocument(id, currentUser(request));
            if (document.isPresent()) {
                purgeDocument(document.get());
                operationLogService.record(currentUser(request), "PURGE_DOCUMENT", "DOCUMENT", id, request.getRemoteAddr(), "SUCCESS");
                purgedCount++;
            }
        }
        redirectAttributes.addFlashAttribute("success", "已彻底删除 " + purgedCount + " 个文档");
        return "redirect:/recycle-bin";
    }

    @PostMapping("/recycle-bin/empty")
    @Transactional
    public String emptyRecycleBin(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        LoginUser currentUser = currentUser(request);
        List<Document> documents = documentService.list(Wrappers.<Document>lambdaQuery().eq(Document::getDeleted, 1))
                .stream()
                .filter(document -> canAccessDeleted(document, currentUser))
                .toList();
        for (Document document : documents) {
            purgeDocument(document);
            operationLogService.record(currentUser, "PURGE_DOCUMENT", "DOCUMENT", document.getId(), request.getRemoteAddr(), "SUCCESS");
        }
        redirectAttributes.addFlashAttribute("success", "已清空回收站，共清理 " + documents.size() + " 个文档");
        return "redirect:/recycle-bin";
    }

    private List<HomeController.DocumentView> filterDocuments(String keyword, Long categoryId, Long tagId, String view, String sort, LoginUser currentUser) {
        List<Long> tagDocumentIds = tagId == null
                ? Collections.emptyList()
                : tagRelService.list(Wrappers.<DocumentTagRel>lambdaQuery().eq(DocumentTagRel::getTagId, tagId))
                .stream()
                .map(DocumentTagRel::getDocumentId)
                .toList();
        LocalDateTime recentCutoff = LocalDateTime.now().minusDays(7);

        Comparator<Document> comparator = documentComparator(sort);
        return documentService.list()
                .stream()
                .filter(document -> !deleted(document))
                .filter(document -> canAccess(document, currentUser))
                .filter(document -> reviewVisible(document, currentUser))
                .filter(document -> !"recent".equals(normalizeView(view))
                        || (document.getUploadedAt() != null && !document.getUploadedAt().isBefore(recentCutoff)))
                .filter(document -> categoryId == null || categoryId.equals(document.getCategoryId()))
                .filter(document -> tagId == null || tagDocumentIds.contains(document.getId()))
                .filter(document -> matchesKeyword(document, keyword))
                .sorted(comparator)
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
        if (document == null || deleted(document) || !canAccess(document, currentUser)) {
            return Optional.empty();
        }
        return Optional.of(document);
    }

    private boolean canAccess(Document document, LoginUser currentUser) {
        return currentUser.isAdmin() || resolveUserId(currentUser).equals(document.getUploadUserId());
    }

    private boolean canAccessDeleted(Document document, LoginUser currentUser) {
        return currentUser.isAdmin() || resolveUserId(currentUser).equals(document.getUploadUserId());
    }

    private boolean reviewVisible(Document document, LoginUser currentUser) {
        return "APPROVED".equals(normalizeReviewStatus(document.getReviewStatus()))
                || currentUser.isAdmin()
                || resolveUserId(currentUser).equals(document.getUploadUserId());
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

    private void moveToRecycleBin(Document document) {
        document.setDeleted(1);
        document.setDeletedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentService.updateById(document);
        taskService.remove(Wrappers.<DocumentTask>lambdaQuery().eq(DocumentTask::getDocumentId, document.getId()));
    }

    private Optional<Document> findRecycleDocument(Long id, LoginUser currentUser) {
        Document document = documentService.getById(id);
        if (document == null || !deleted(document) || !canAccessDeleted(document, currentUser)) {
            return Optional.empty();
        }
        return Optional.of(document);
    }

    private void restoreDocument(Document document) {
        document.setDeleted(0);
        document.setDeletedAt(null);
        document.setUpdatedAt(LocalDateTime.now());
        documentService.updateById(document);
    }

    private void purgeDocument(Document document) {
        tagRelService.remove(Wrappers.<DocumentTagRel>lambdaQuery().eq(DocumentTagRel::getDocumentId, document.getId()));
        taskService.remove(Wrappers.<DocumentTask>lambdaQuery().eq(DocumentTask::getDocumentId, document.getId()));
        String storagePath = document.getStoragePath();
        documentService.removeById(document.getId());
        fileStorageService.deleteIfExists(storagePath);
    }

    private String recycleCountdown(Document document) {
        if (document.getDeletedAt() == null) {
            return "剩余 30 天";
        }
        long days = java.time.Duration.between(LocalDateTime.now(), document.getDeletedAt().plusDays(30)).toDays();
        return days <= 0 ? "已到期，可清理" : "剩余 " + days + " 天";
    }

    private boolean deleted(Document document) {
        return document.getDeleted() != null && document.getDeleted() == 1;
    }

    private Comparator<Document> documentComparator(String sort) {
        return switch (normalizeSort(sort)) {
            case "title_asc" -> Comparator.comparing(Document::getTitle, Comparator.nullsLast(String::compareToIgnoreCase));
            case "updated_desc" -> Comparator.comparing(Document::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed();
            case "size_desc" -> Comparator.comparing(Document::getFileSize, Comparator.nullsLast(Long::compareTo)).reversed();
            default -> Comparator.comparing(Document::getUploadedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed();
        };
    }

    private String normalizeSort(String sort) {
        if ("title_asc".equals(sort) || "updated_desc".equals(sort) || "size_desc".equals(sort)) {
            return sort;
        }
        return "created_desc";
    }

    private String normalizeDisplay(String display) {
        return "grid".equals(display) ? "grid" : "list";
    }

    private String normalizeReviewStatus(String reviewStatus) {
        if ("APPROVED".equals(reviewStatus) || "REJECTED".equals(reviewStatus)) {
            return reviewStatus;
        }
        return "PENDING";
    }

    private MediaType previewMediaType(String fileType) {
        String normalized = fileType == null ? "" : fileType.toUpperCase();
        return switch (normalized) {
            case "PNG" -> MediaType.IMAGE_PNG;
            case "JPG", "JPEG" -> MediaType.IMAGE_JPEG;
            case "PDF" -> MediaType.APPLICATION_PDF;
            case "TXT", "CSV" -> MediaType.TEXT_PLAIN;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private String previewMode(String fileType) {
        String normalized = fileType == null ? "" : fileType.toUpperCase();
        if (List.of("PNG", "JPG", "JPEG").contains(normalized)) {
            return "image";
        }
        if (List.of("PDF", "TXT", "CSV").contains(normalized)) {
            return "frame";
        }
        return "placeholder";
    }

    private void createPendingTaskIfNeeded(Long documentId, Long userId, List<Long> tagIds) {
        boolean hasPendingTag = tagIds.stream()
                .map(tagService::getById)
                .filter(java.util.Objects::nonNull)
                .anyMatch(tag -> "待处理".equals(tag.getName()));
        if (!hasPendingTag) {
            return;
        }
        boolean exists = taskService.exists(Wrappers.<DocumentTask>lambdaQuery()
                .eq(DocumentTask::getDocumentId, documentId)
                .eq(DocumentTask::getUserId, userId)
                .eq(DocumentTask::getStatus, "PENDING"));
        if (exists) {
            return;
        }
        DocumentTask task = new DocumentTask();
        task.setDocumentId(documentId);
        task.setUserId(userId);
        task.setStatus("PENDING");
        task.setPriority("MEDIUM");
        task.setDueAt(LocalDateTime.now().plusDays(1));
        task.setNote("文档带有待处理标签，请及时处理");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskService.save(task);
    }

    private String normalizeView(String view) {
        return "recent".equals(view) ? "recent" : "all";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    public record RecycleDocumentView(HomeController.DocumentView document, String countdown) {
    }
}
