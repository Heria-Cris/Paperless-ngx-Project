package com.paperless.local.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class HomeController {

    private static final List<DocumentView> DOCUMENTS = List.of(
            new DocumentView(1999, "Newest Correspondent", "H7_Napoleon_Bonaparte_zadanie", List.of("Another Sample Tag", "Inbox"), "User2", "Invoice Test", "Aug 9, 2023", "Aug 9, 2023", "PDF"),
            new DocumentView(0, "Test Correspondent 1", "[paperless] test post-owner", List.of("Inbox", "Tag 2"), "Test User", "Invoice Test", "Mar 25, 2023", "Dec 13, 2022", "PDF"),
            new DocumentView(0, "Correspondent 9", "1 Testing New Title Updated 2", List.of("Another Sample Tag", "Inbox", "TagWithPartial"), "User2", "", "Oct 2, 2022", "Oct 2, 2022", "DOCX"),
            new DocumentView(112412326, "Newest Correspondent", "Sample100.csv", List.of("Inbox", "Just another tag", "Tag 2"), "User2", "", "Oct 2, 2022", "Oct 2, 2022", "CSV"),
            new DocumentView(0, "Test Correspondent 1", "UM_PPBE_en_v29", List.of("Another Sample Tag"), "User2", "Invoice Test", "Oct 1, 2022", "Oct 2, 2022", "PDF"),
            new DocumentView(0, "Correspondent 14", "Review-of-New-York-Federal-Petitions-article", List.of("Partial Tag", "Tag 2", "Test Tag"), "User2", "Invoice Test", "Mar 12, 2022", "Mar 13, 2022", "PDF")
    );

    private static final List<LookupView> CATEGORIES = List.of(
            new LookupView("合同", "业务合同、协议等文件", 8),
            new LookupView("发票", "财务票据和报销材料", 12),
            new LookupView("证书", "证书扫描件和证明材料", 5),
            new LookupView("课程资料", "课件、作业和实训材料", 21),
            new LookupView("报告", "总结、调研和分析报告", 6)
    );

    private static final List<LookupView> TAGS = List.of(
            new LookupView("重要", "#c9773d", 14),
            new LookupView("待处理", "#62c7bd", 9),
            new LookupView("已归档", "#51a548", 18),
            new LookupView("学校", "#5d35b7", 11),
            new LookupView("公司", "#9b4bd8", 7)
    );

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("pageTitle", "登录");
        return "login";
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        prepareApp(model, "dashboard", "Dashboard");
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

    @GetMapping("/categories")
    public String categories(Model model) {
        prepareApp(model, "categories", "Document Types");
        return "app";
    }

    @GetMapping("/tags")
    public String tags(Model model) {
        prepareApp(model, "tags", "Tags");
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
        model.addAttribute("activePage", activePage);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("documents", DOCUMENTS);
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("tags", TAGS);
        model.addAttribute("documentTotal", 64);
        model.addAttribute("inboxTotal", 5);
        model.addAttribute("categoryTotal", CATEGORIES.size());
        model.addAttribute("tagTotal", TAGS.size());
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

    public record LookupView(String name, String description, int count) {
    }
}
