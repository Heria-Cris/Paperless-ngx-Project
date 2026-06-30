package com.paperless.local.controller;

import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.paperless.local.entity.Document;
import com.paperless.local.entity.DocumentCategory;
import com.paperless.local.service.DocumentCategoryService;
import com.paperless.local.service.DocumentService;

@Controller
public class CategoryController {

    private final DocumentCategoryService categoryService;
    private final DocumentService documentService;
    private final HomeController homeController;

    public CategoryController(
            DocumentCategoryService categoryService,
            DocumentService documentService,
            HomeController homeController
    ) {
        this.categoryService = categoryService;
        this.documentService = documentService;
        this.homeController = homeController;
    }

    @GetMapping("/categories")
    public String categories(@RequestParam(name = "editId", required = false) Long editId, Model model) {
        homeController.prepareManagePage(model, "categories", "分类管理", editId);
        return "app";
    }

    @PostMapping("/categories")
    public String create(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes
    ) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "分类名称不能为空");
            return "redirect:/categories";
        }
        if (categoryService.exists(Wrappers.<DocumentCategory>lambdaQuery().eq(DocumentCategory::getName, trimmedName))) {
            redirectAttributes.addFlashAttribute("error", "分类名称已存在");
            return "redirect:/categories";
        }

        DocumentCategory category = new DocumentCategory();
        category.setName(trimmedName);
        category.setDescription(description);
        categoryService.save(category);
        redirectAttributes.addFlashAttribute("success", "分类创建成功");
        return "redirect:/categories";
    }

    @PostMapping("/categories/{id}/update")
    public String update(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes
    ) {
        Optional<DocumentCategory> existing = Optional.ofNullable(categoryService.getById(id));
        String trimmedName = name == null ? "" : name.trim();
        if (existing.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "分类不存在");
            return "redirect:/categories";
        }
        if (trimmedName.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "分类名称不能为空");
            return "redirect:/categories?editId=" + id;
        }
        boolean duplicated = categoryService.exists(Wrappers.<DocumentCategory>lambdaQuery()
                .eq(DocumentCategory::getName, trimmedName)
                .ne(DocumentCategory::getId, id));
        if (duplicated) {
            redirectAttributes.addFlashAttribute("error", "分类名称已存在");
            return "redirect:/categories?editId=" + id;
        }

        DocumentCategory category = existing.get();
        category.setName(trimmedName);
        category.setDescription(description);
        categoryService.updateById(category);
        redirectAttributes.addFlashAttribute("success", "分类更新成功");
        return "redirect:/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        long documentCount = documentService.count(Wrappers.<Document>lambdaQuery().eq(Document::getCategoryId, id));
        if (documentCount > 0) {
            redirectAttributes.addFlashAttribute("error", "该分类下存在文档，不能直接删除");
            return "redirect:/categories";
        }
        boolean removed = categoryService.removeById(id);
        redirectAttributes.addFlashAttribute(removed ? "success" : "error", removed ? "分类删除成功" : "分类不存在");
        return "redirect:/categories";
    }
}
