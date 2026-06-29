package com.paperless.local.controller;

import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.paperless.local.entity.Document;
import com.paperless.local.entity.DocumentTagRel;
import com.paperless.local.service.DocumentCategoryService;
import com.paperless.local.service.DocumentService;
import com.paperless.local.service.DocumentTagRelService;
import com.paperless.local.service.DocumentTagService;

@Controller
public class HomeController {

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

    private static final List<DocumentView> DOCUMENTS = List.of(
            new DocumentView(1999, "Newest Correspondent", "H7_Napoleon_Bonaparte_zadanie", List.of("Another Sample Tag", "Inbox"), "User2", "Invoice Test", "Aug 9, 2023", "Aug 9, 2023", "PDF"),
            new DocumentView(0, "Test Correspondent 1", "[paperless] test post-owner", List.of("Inbox", "Tag 2"), "Test User", "Invoice Test", "Mar 25, 2023", "Dec 13, 2022", "PDF"),
            new DocumentView(0, "Correspondent 9", "1 Testing New Title Updated 2", List.of("Another Sample Tag", "Inbox", "TagWithPartial"), "User2", "", "Oct 2, 2022", "Oct 2, 2022", "DOCX"),
            new DocumentView(112412326, "Newest Correspondent", "Sample100.csv", List.of("Inbox", "Just another tag", "Tag 2"), "User2", "", "Oct 2, 2022", "Oct 2, 2022", "CSV"),
            new DocumentView(0, "Test Correspondent 1", "UM_PPBE_en_v29", List.of("Another Sample Tag"), "User2", "Invoice Test", "Oct 1, 2022", "Oct 2, 2022", "PDF"),
            new DocumentView(0, "Correspondent 14", "Review-of-New-York-Federal-Petitions-article", List.of("Partial Tag", "Tag 2", "Test Tag"), "User2", "Invoice Test", "Mar 12, 2022", "Mar 13, 2022", "PDF")
    );

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@RequestParam(name = "denied", required = false) String denied, Model model) {
        prepareApp(model, "dashboard", "Dashboard");
        if (denied != null) {
            model.addAttribute("warning", "当前账号无权访问该管理页面");
        }
        return "app";
    }

    @GetMapping("/documents")
    public String documents(Model model) {
        prepareApp(model, "documents", "Documents");
        return "app";
    }

    @GetMapping("/documents/upload")
    public String upload(Model model) {
        prepareApp(model, "upload", "Upload documents");
        return "app";
    }

    @GetMapping("/documents/{id}")
    public String documentDetail(@PathVariable Integer id, Model model) {
        prepareApp(model, "document-detail", "Document detail");
        model.addAttribute("selectedDocument", DOCUMENTS.get(Math.floorMod(id, DOCUMENTS.size())));
        return "app";
    }

    @GetMapping("/documents/{id}/edit")
    public String documentEdit(@PathVariable Integer id, Model model) {
        prepareApp(model, "document-edit", "Edit document");
        model.addAttribute("selectedDocument", DOCUMENTS.get(Math.floorMod(id, DOCUMENTS.size())));
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

    private void prepareApp(Model model, String activePage, String pageTitle) {
        List<LookupView> categories = categoryViews();
        List<LookupView> tags = tagViews();
        model.addAttribute("activePage", activePage);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("documents", DOCUMENTS);
        model.addAttribute("categories", categories);
        model.addAttribute("tags", tags);
        model.addAttribute("documentTotal", documentService.count());
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

    public record DocumentView(
            int asn,
            String correspondent,
            String title,
            List<String> tags,
            String owner,
            String documentType,
            String created,
            String added,
            String fileType
    ) {
    }

    public record LookupView(Long id, String name, String description, long count) {
    }
}
