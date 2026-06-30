package com.paperless.local.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.paperless.local.entity.Document;
import com.paperless.local.entity.DocumentTag;
import com.paperless.local.entity.DocumentTagRel;
import com.paperless.local.entity.DocumentTask;
import com.paperless.local.model.LoginUser;
import com.paperless.local.service.DocumentService;
import com.paperless.local.service.DocumentTagRelService;
import com.paperless.local.service.DocumentTagService;
import com.paperless.local.service.DocumentTaskService;
import com.paperless.local.service.FileStorageService;
import com.paperless.local.service.OperationLogService;

@Controller
public class FileTaskController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DocumentTaskService taskService;
    private final DocumentService documentService;
    private final DocumentTagService tagService;
    private final DocumentTagRelService tagRelService;
    private final FileStorageService fileStorageService;
    private final OperationLogService operationLogService;
    private final HomeController homeController;

    public FileTaskController(
            DocumentTaskService taskService,
            DocumentService documentService,
            DocumentTagService tagService,
            DocumentTagRelService tagRelService,
            FileStorageService fileStorageService,
            OperationLogService operationLogService,
            HomeController homeController
    ) {
        this.taskService = taskService;
        this.documentService = documentService;
        this.tagService = tagService;
        this.tagRelService = tagRelService;
        this.fileStorageService = fileStorageService;
        this.operationLogService = operationLogService;
        this.homeController = homeController;
    }

    @GetMapping("/file-tasks")
    public String fileTasks(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "5") Integer size,
            HttpServletRequest request,
            Model model
    ) {
        LoginUser currentUser = currentUser(request);
        ensurePendingTagTasks(currentUser);
        homeController.prepareApp(model, "file-tasks", "文件任务", currentUser);
        List<DocumentTask> tasks = taskService.list(Wrappers.<DocumentTask>lambdaQuery()
                        .eq(DocumentTask::getUserId, currentUser.id()))
                .stream()
                .sorted(this::compareTasks)
                .toList();
        List<TaskView> taskViews = tasks.stream().map(this::taskView).toList();
        int pageSize = normalizePageSize(size);
        int taskTotal = taskViews.size();
        int totalPages = Math.max(1, (int) Math.ceil(taskTotal / (double) pageSize));
        int currentPage = Math.min(Math.max(page == null ? 1 : page, 1), totalPages);
        int fromIndex = Math.min((currentPage - 1) * pageSize, taskTotal);
        int toIndex = Math.min(fromIndex + pageSize, taskTotal);
        model.addAttribute("fileTasks", taskViews.subList(fromIndex, toIndex));
        model.addAttribute("taskTotal", taskTotal);
        model.addAttribute("taskPage", currentPage);
        model.addAttribute("taskPageSize", pageSize);
        model.addAttribute("taskTotalPages", totalPages);
        model.addAttribute("taskHasPrevious", currentPage > 1);
        model.addAttribute("taskHasNext", currentPage < totalPages);
        model.addAttribute("taskPreviousPage", Math.max(1, currentPage - 1));
        model.addAttribute("taskNextPage", Math.min(totalPages, currentPage + 1));
        model.addAttribute("availableTaskDocuments", visibleDocuments(currentUser));
        model.addAttribute("pendingTaskTotal", taskViews.stream().filter(task -> "PENDING".equals(task.status())).count());
        model.addAttribute("overdueTaskTotal", taskViews.stream().filter(TaskView::overdue).count());
        return "app";
    }

    @PostMapping("/file-tasks")
    @Transactional
    public String createTask(
            @RequestParam Long documentId,
            @RequestParam String dueAt,
            @RequestParam(defaultValue = "MEDIUM") String priority,
            @RequestParam(required = false) String note,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        LoginUser currentUser = currentUser(request);
        Optional<Document> document = accessibleDocument(documentId, currentUser);
        if (document.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "文档不存在或无权创建任务");
            return "redirect:/file-tasks";
        }
        LocalDateTime dueTime = parseDateTime(dueAt);
        if (dueTime == null) {
            redirectAttributes.addFlashAttribute("error", "请设置有效的最晚处理时间");
            return "redirect:/file-tasks";
        }
        DocumentTask task = new DocumentTask();
        task.setDocumentId(documentId);
        task.setUserId(currentUser.id());
        task.setStatus("PENDING");
        task.setPriority(normalizePriority(priority));
        task.setDueAt(dueTime);
        task.setNote(trim(note));
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskService.save(task);
        operationLogService.record(currentUser, "CREATE_FILE_TASK", "DOCUMENT_TASK", task.getId(), request.getRemoteAddr(), "SUCCESS");
        redirectAttributes.addFlashAttribute("success", "文件任务已创建");
        return "redirect:/file-tasks";
    }

    @PostMapping("/file-tasks/{id}/replace-file")
    @Transactional
    public String replaceFile(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        LoginUser currentUser = currentUser(request);
        Optional<DocumentTask> existing = ownedTask(id, currentUser);
        if (existing.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "任务不存在或无权更新文件");
            return "redirect:/file-tasks";
        }
        DocumentTask task = existing.get();
        Document document = documentService.getById(task.getDocumentId());
        if (document == null) {
            redirectAttributes.addFlashAttribute("error", "关联文档不存在");
            return "redirect:/file-tasks";
        }

        String oldStoragePath = document.getStoragePath();
        FileStorageService.StoredFile storedFile = null;
        try {
            storedFile = fileStorageService.store(file, document.getUploadUserId());
            document.setOriginalFilename(storedFile.originalFilename());
            document.setStoredFilename(storedFile.storedFilename());
            document.setStoragePath(storedFile.storagePath());
            document.setFileSize(storedFile.fileSize());
            document.setFileType(storedFile.fileType());
            document.setUpdatedAt(LocalDateTime.now());
            documentService.updateById(document);
            if (oldStoragePath != null && !oldStoragePath.equals(storedFile.storagePath())) {
                fileStorageService.deleteIfExists(oldStoragePath);
            }

            task.setStatus("DONE");
            task.setHandledAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskService.updateById(task);
            operationLogService.record(currentUser, "REPLACE_TASK_DOCUMENT", "DOCUMENT", document.getId(), request.getRemoteAddr(), "SUCCESS");
            operationLogService.record(currentUser, "COMPLETE_FILE_TASK", "DOCUMENT_TASK", id, request.getRemoteAddr(), "SUCCESS");
            redirectAttributes.addFlashAttribute("success", "文件已覆盖更新，任务已标记为已处理");
        } catch (Exception ex) {
            if (storedFile != null) {
                fileStorageService.deleteIfExists(storedFile.storagePath());
            }
            redirectAttributes.addFlashAttribute("error", "文件覆盖失败：" + ex.getMessage());
        }
        return "redirect:/file-tasks";
    }

    @PostMapping("/file-tasks/{id}/update")
    public String updateTask(
            @PathVariable Long id,
            @RequestParam String dueAt,
            @RequestParam(defaultValue = "MEDIUM") String priority,
            @RequestParam(required = false) String note,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        LoginUser currentUser = currentUser(request);
        Optional<DocumentTask> existing = ownedTask(id, currentUser);
        if (existing.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "任务不存在或无权更新");
            return "redirect:/file-tasks";
        }
        LocalDateTime dueTime = parseDateTime(dueAt);
        if (dueTime == null) {
            redirectAttributes.addFlashAttribute("error", "请设置有效的最晚处理时间");
            return "redirect:/file-tasks";
        }
        DocumentTask task = existing.get();
        task.setDueAt(dueTime);
        task.setPriority(normalizePriority(priority));
        task.setNote(trim(note));
        task.setUpdatedAt(LocalDateTime.now());
        taskService.updateById(task);
        operationLogService.record(currentUser, "UPDATE_FILE_TASK", "DOCUMENT_TASK", id, request.getRemoteAddr(), "SUCCESS");
        redirectAttributes.addFlashAttribute("success", "任务已更新");
        return "redirect:/file-tasks";
    }

    @PostMapping("/file-tasks/{id}/complete")
    public String completeTask(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        LoginUser currentUser = currentUser(request);
        Optional<DocumentTask> existing = ownedTask(id, currentUser);
        if (existing.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "任务不存在或无权处理");
            return "redirect:/file-tasks";
        }
        DocumentTask task = existing.get();
        task.setStatus("DONE");
        task.setHandledAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskService.updateById(task);
        operationLogService.record(currentUser, "COMPLETE_FILE_TASK", "DOCUMENT_TASK", id, request.getRemoteAddr(), "SUCCESS");
        redirectAttributes.addFlashAttribute("success", "任务已标记为已处理");
        return "redirect:/file-tasks";
    }

    private List<Document> visibleDocuments(LoginUser currentUser) {
        return documentService.list(Wrappers.<Document>lambdaQuery()
                        .orderByDesc(Document::getUploadedAt)
                        .orderByDesc(Document::getId))
                .stream()
                .filter(document -> currentUser.isAdmin() || currentUser.id().equals(document.getUploadUserId()))
                .toList();
    }

    private void ensurePendingTagTasks(LoginUser currentUser) {
        DocumentTag pendingTag = tagService.getOne(Wrappers.<DocumentTag>lambdaQuery().eq(DocumentTag::getName, "待处理"), false);
        if (pendingTag == null) {
            return;
        }
        List<Long> pendingDocumentIds = tagRelService.list(Wrappers.<DocumentTagRel>lambdaQuery()
                        .eq(DocumentTagRel::getTagId, pendingTag.getId()))
                .stream()
                .map(DocumentTagRel::getDocumentId)
                .toList();
        visibleDocuments(currentUser).stream()
                .filter(document -> pendingDocumentIds.contains(document.getId()))
                .forEach(document -> createTaskForPendingDocumentIfAbsent(document, currentUser));
    }

    private void createTaskForPendingDocumentIfAbsent(Document document, LoginUser currentUser) {
        boolean exists = taskService.exists(Wrappers.<DocumentTask>lambdaQuery()
                .eq(DocumentTask::getDocumentId, document.getId())
                .eq(DocumentTask::getUserId, currentUser.id()));
        if (exists) {
            return;
        }
        DocumentTask task = new DocumentTask();
        task.setDocumentId(document.getId());
        task.setUserId(currentUser.id());
        task.setStatus("PENDING");
        task.setPriority("MEDIUM");
        task.setDueAt(LocalDateTime.now().plusDays(1));
        task.setNote("文档带有待处理标签，请及时处理");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskService.save(task);
    }

    private Optional<Document> accessibleDocument(Long documentId, LoginUser currentUser) {
        Document document = documentService.getById(documentId);
        if (document == null || (!currentUser.isAdmin() && !currentUser.id().equals(document.getUploadUserId()))) {
            return Optional.empty();
        }
        return Optional.of(document);
    }

    private Optional<DocumentTask> ownedTask(Long id, LoginUser currentUser) {
        DocumentTask task = taskService.getById(id);
        if (task == null || !currentUser.id().equals(task.getUserId())) {
            return Optional.empty();
        }
        return Optional.of(task);
    }

    private TaskView taskView(DocumentTask task) {
        Document document = documentService.getById(task.getDocumentId());
        LocalDateTime now = LocalDateTime.now();
        boolean pending = "PENDING".equals(task.getStatus());
        boolean overdue = pending && task.getDueAt() != null && task.getDueAt().isBefore(now);
        boolean dueSoon = pending && !overdue && task.getDueAt() != null && task.getDueAt().isBefore(now.plusHours(24));
        return new TaskView(
                task.getId(),
                task.getDocumentId(),
                document == null ? "文档已不存在" : document.getTitle(),
                task.getStatus(),
                statusText(task.getStatus()),
                task.getPriority(),
                priorityText(task.getPriority()),
                task.getDueAt() == null ? "" : task.getDueAt().format(DATE_TIME_FORMATTER),
                toDatetimeLocal(task.getDueAt()),
                task.getHandledAt() == null ? "" : task.getHandledAt().format(DATE_TIME_FORMATTER),
                task.getNote(),
                overdue,
                dueSoon,
                urgencyText(overdue, dueSoon, task.getPriority())
        );
    }

    private int compareTasks(DocumentTask left, DocumentTask right) {
        int statusCompare = Boolean.compare(!"PENDING".equals(left.getStatus()), !"PENDING".equals(right.getStatus()));
        if (statusCompare != 0) {
            return statusCompare;
        }
        int overdueCompare = Boolean.compare(!isOverdue(left), !isOverdue(right));
        if (overdueCompare != 0) {
            return overdueCompare;
        }
        int priorityCompare = Integer.compare(priorityRank(right.getPriority()), priorityRank(left.getPriority()));
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return Comparator.nullsLast(LocalDateTime::compareTo).compare(left.getDueAt(), right.getDueAt());
    }

    private boolean isOverdue(DocumentTask task) {
        return "PENDING".equals(task.getStatus()) && task.getDueAt() != null && task.getDueAt().isBefore(LocalDateTime.now());
    }

    private int priorityRank(String priority) {
        return switch (normalizePriority(priority)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private int normalizePageSize(Integer size) {
        if (size == null) {
            return 5;
        }
        if (size <= 5) {
            return 5;
        }
        if (size <= 10) {
            return 10;
        }
        return 20;
    }

    private String normalizePriority(String priority) {
        if ("HIGH".equals(priority) || "LOW".equals(priority)) {
            return priority;
        }
        return "MEDIUM";
    }

    private String priorityText(String priority) {
        return switch (normalizePriority(priority)) {
            case "HIGH" -> "高";
            case "LOW" -> "低";
            default -> "中";
        };
    }

    private String statusText(String status) {
        return "DONE".equals(status) ? "已处理" : "待处理";
    }

    private String urgencyText(boolean overdue, boolean dueSoon, String priority) {
        if (overdue) {
            return "已逾期";
        }
        if (dueSoon) {
            return "24 小时内到期";
        }
        return "HIGH".equals(priority) ? "高优先级" : "正常";
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String toDatetimeLocal(LocalDateTime time) {
        return time == null ? "" : time.toString().substring(0, 16);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private LoginUser currentUser(HttpServletRequest request) {
        return (LoginUser) request.getAttribute("currentUser");
    }

    public record TaskView(
            Long id,
            Long documentId,
            String documentTitle,
            String status,
            String statusText,
            String priority,
            String priorityText,
            String dueAt,
            String dueAtInput,
            String handledAt,
            String note,
            boolean overdue,
            boolean dueSoon,
            String urgency
    ) {
    }
}
