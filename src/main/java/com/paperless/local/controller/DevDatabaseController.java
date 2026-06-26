package com.paperless.local.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paperless.local.service.DocumentCategoryService;
import com.paperless.local.service.DocumentService;
import com.paperless.local.service.DocumentTagRelService;
import com.paperless.local.service.DocumentTagService;
import com.paperless.local.service.OperationLogService;
import com.paperless.local.service.UserService;

@RestController
public class DevDatabaseController {

    private final UserService userService;
    private final DocumentService documentService;
    private final DocumentCategoryService categoryService;
    private final DocumentTagService tagService;
    private final DocumentTagRelService tagRelService;
    private final OperationLogService operationLogService;

    public DevDatabaseController(
            UserService userService,
            DocumentService documentService,
            DocumentCategoryService categoryService,
            DocumentTagService tagService,
            DocumentTagRelService tagRelService,
            OperationLogService operationLogService
    ) {
        this.userService = userService;
        this.documentService = documentService;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.tagRelService = tagRelService;
        this.operationLogService = operationLogService;
    }

    @GetMapping("/dev/database-check")
    public Map<String, Object> databaseCheck() {
        return Map.of(
                "status", "ok",
                "users", userService.count(),
                "documents", documentService.count(),
                "categories", categoryService.count(),
                "tags", tagService.count(),
                "documentTagRelations", tagRelService.count(),
                "operationLogs", operationLogService.count()
        );
    }
}
