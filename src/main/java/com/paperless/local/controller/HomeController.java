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
import com.paperless.local.entity.DocumentTask;
import com.paperless.local.entity.OperationLog;
import com.paperless.local.entity.User;
import com.paperless.local.model.LoginUser;
import com.paperless.local.service.DocumentCategoryService;
import com.paperless.local.service.DocumentService;
import com.paperless.local.service.DocumentTagRelService;
import com.paperless.local.service.DocumentTagService;
import com.paperless.local.service.DocumentTaskService;
import com.paperless.local.service.OperationLogService;
import com.paperless.local.service.UserService;

@Controller
public class HomeController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DocumentService documentService;
    private final DocumentCategoryService categoryService;
    private final DocumentTagService tagService;
    private final DocumentTagRelService tagRelService;
    private final UserService userService;
    private final DocumentTaskService taskService;
    private final OperationLogService operationLogService;

    public HomeController(
            DocumentService documentService,
            DocumentCategoryService categoryService,
            DocumentTagService tagService,
            DocumentTagRelService tagRelService,
            UserService userService,
            DocumentTaskService taskService,
            OperationLogService operationLogService
    ) {
        this.documentService = documentService;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.tagRelService = tagRelService;
        this.userService = userService;
        this.taskService = taskService;
        this.operationLogService = operationLogService;
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
    public String logs(HttpServletRequest request, Model model) {
        prepareApp(model, "logs", "操作日志", currentUser(request));
        model.addAttribute("operationLogs", operationLogViews());
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
        addTaskSummary(model, currentUser);
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
                        documentService.count(Wrappers.<Document>lambdaQuery()
                                .eq(Document::getDeleted, 0)
                                .eq(Document::getCategoryId, category.getId()))
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
                tagIds,
                reviewStatusText(document.getReviewStatus()),
                document.getReviewStatus() == null ? "PENDING" : document.getReviewStatus(),
                document.getReviewComment() == null ? "" : document.getReviewComment(),
                document.getDeletedAt() == null ? "" : document.getDeletedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        );
    }

    public List<DocumentView> documentViews() {
        return documentViews(null);
    }

    public List<DocumentView> documentViews(LoginUser currentUser) {
        return documentService.list(Wrappers.<Document>lambdaQuery()
                        .eq(Document::getDeleted, 0)
                        .orderByDesc(Document::getUploadedAt)
                        .orderByDesc(Document::getId))
                .stream()
                .filter(document -> canAccess(document, currentUser))
                .filter(document -> reviewVisible(document, currentUser))
                .map(this::documentView)
                .toList();
    }

    private boolean canAccess(Document document, LoginUser currentUser) {
        return currentUser == null || currentUser.isAdmin() || resolveUserId(currentUser).equals(document.getUploadUserId());
    }

    private boolean reviewVisible(Document document, LoginUser currentUser) {
        return "APPROVED".equals(normalizeReviewStatus(document.getReviewStatus()))
                || currentUser == null
                || currentUser.isAdmin()
                || resolveUserId(currentUser).equals(document.getUploadUserId());
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

    private void addTaskSummary(Model model, LoginUser currentUser) {
        if (currentUser == null) {
            model.addAttribute("pendingTaskTotal", 0L);
            model.addAttribute("overdueTaskTotal", 0L);
            return;
        }
        List<DocumentTask> pendingTasks = taskService.list(Wrappers.<DocumentTask>lambdaQuery()
                .eq(DocumentTask::getUserId, currentUser.id())
                .eq(DocumentTask::getStatus, "PENDING"))
                .stream()
                .filter(task -> activeTaskDocument(task.getDocumentId(), currentUser))
                .toList();
        long pendingTaggedDocumentCount = pendingTaggedDocumentCount(currentUser);
        long overdueCount = pendingTasks.stream()
                .filter(task -> task.getDueAt() != null && task.getDueAt().isBefore(java.time.LocalDateTime.now()))
                .count();
        long dueSoonCount = pendingTasks.stream()
                .filter(task -> task.getDueAt() != null
                        && !task.getDueAt().isBefore(java.time.LocalDateTime.now())
                        && task.getDueAt().isBefore(java.time.LocalDateTime.now().plusHours(24)))
                .count();
        model.addAttribute("pendingTaskTotal", pendingTasks.size() + pendingTaggedDocumentCount);
        model.addAttribute("overdueTaskTotal", overdueCount);
        model.addAttribute("dueSoonTaskTotal", dueSoonCount);
        if (overdueCount > 0) {
            model.addAttribute("taskWarning", "你有 " + overdueCount + " 个文件任务已逾期，请尽快处理。");
        } else if (dueSoonCount > 0) {
            model.addAttribute("taskNotice", "你有 " + dueSoonCount + " 个文件任务将在 24 小时内到期。");
        }
    }

    private long pendingTaggedDocumentCount(LoginUser currentUser) {
        com.paperless.local.entity.DocumentTag pendingTag = tagService.getOne(Wrappers.<com.paperless.local.entity.DocumentTag>lambdaQuery()
                .eq(com.paperless.local.entity.DocumentTag::getName, "待处理"), false);
        if (pendingTag == null) {
            return 0;
        }
        List<Long> pendingDocumentIds = tagRelService.list(Wrappers.<DocumentTagRel>lambdaQuery()
                        .eq(DocumentTagRel::getTagId, pendingTag.getId()))
                .stream()
                .map(DocumentTagRel::getDocumentId)
                .toList();
        if (pendingDocumentIds.isEmpty()) {
            return 0;
        }
        return documentService.list(Wrappers.<Document>lambdaQuery()
                        .in(Document::getId, pendingDocumentIds))
                .stream()
                .filter(document -> canAccess(document, currentUser))
                .filter(document -> document.getDeleted() == null || document.getDeleted() == 0)
                .filter(document -> !taskService.exists(Wrappers.<DocumentTask>lambdaQuery()
                        .eq(DocumentTask::getUserId, currentUser.id())
                        .eq(DocumentTask::getDocumentId, document.getId())))
                .count();
    }

    private boolean activeTaskDocument(Long documentId, LoginUser currentUser) {
        Document document = documentService.getById(documentId);
        return document != null
                && (document.getDeleted() == null || document.getDeleted() == 0)
                && canAccess(document, currentUser);
    }

    private List<OperationLogView> operationLogViews() {
        return operationLogService.list(Wrappers.<OperationLog>lambdaQuery()
                        .orderByDesc(OperationLog::getCreatedAt)
                        .orderByDesc(OperationLog::getId)
                        .last("LIMIT 100"))
                .stream()
                .map(log -> new OperationLogView(
                        log.getCreatedAt() == null ? "" : log.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        log.getUsername(),
                        operationText(log.getOperationType()),
                        log.getTargetType(),
                        log.getTargetId(),
                        log.getIpAddress(),
                        log.getResult()
                ))
                .toList();
    }

    private String operationText(String operationType) {
        if (operationType == null) {
            return "";
        }
        return switch (operationType) {
            case "LOGIN" -> "登录";
            case "LIST_DOCUMENTS" -> "查看文档列表";
            case "VIEW_DOCUMENT" -> "查看文档";
            case "UPLOAD_DOCUMENT" -> "上传文档";
            case "UPDATE_DOCUMENT" -> "编辑文档";
            case "DELETE_DOCUMENT" -> "删除文档";
            case "DOWNLOAD_DOCUMENT" -> "下载文档";
            case "APPROVE_DOCUMENT" -> "审查通过";
            case "REJECT_DOCUMENT" -> "审查打回";
            case "RESTORE_DOCUMENT" -> "恢复文档";
            case "PURGE_DOCUMENT" -> "彻底删除文档";
            case "CREATE_FILE_TASK" -> "创建文件任务";
            case "UPDATE_FILE_TASK" -> "更新文件任务";
            case "COMPLETE_FILE_TASK" -> "完成文件任务";
            case "REPLACE_TASK_DOCUMENT" -> "任务中覆盖文件";
            default -> operationType;
        };
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

    private String normalizeReviewStatus(String reviewStatus) {
        if ("APPROVED".equals(reviewStatus) || "REJECTED".equals(reviewStatus)) {
            return reviewStatus;
        }
        return "PENDING";
    }

    private String reviewStatusText(String reviewStatus) {
        return switch (normalizeReviewStatus(reviewStatus)) {
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已打回";
            default -> "审查中";
        };
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
            List<Long> tagIds,
            String reviewStatusText,
            String reviewStatus,
            String reviewComment,
            String deletedAt
    ) {
    }

    public record LookupView(Long id, String name, String description, long count) {
    }

    public record TagView(Long id, String name, String color) {
    }

    public record OperationLogView(
            String time,
            String username,
            String operation,
            String targetType,
            Long targetId,
            String ipAddress,
            String result
    ) {
    }
}
