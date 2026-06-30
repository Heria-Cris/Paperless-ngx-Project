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

import com.paperless.local.entity.DocumentTag;
import com.paperless.local.entity.DocumentTagRel;
import com.paperless.local.service.DocumentTagRelService;
import com.paperless.local.service.DocumentTagService;

@Controller
public class TagController {

    private final DocumentTagService tagService;
    private final DocumentTagRelService tagRelService;
    private final HomeController homeController;

    public TagController(
            DocumentTagService tagService,
            DocumentTagRelService tagRelService,
            HomeController homeController
    ) {
        this.tagService = tagService;
        this.tagRelService = tagRelService;
        this.homeController = homeController;
    }

    @GetMapping("/tags")
    public String tags(@RequestParam(name = "editId", required = false) Long editId, Model model) {
        homeController.prepareManagePage(model, "tags", "标签管理", editId);
        return "app";
    }

    @PostMapping("/tags")
    public String create(
            @RequestParam String name,
            @RequestParam(defaultValue = "#62c7bd") String color,
            RedirectAttributes redirectAttributes
    ) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "标签名称不能为空");
            return "redirect:/tags";
        }
        if (tagService.exists(Wrappers.<DocumentTag>lambdaQuery().eq(DocumentTag::getName, trimmedName))) {
            redirectAttributes.addFlashAttribute("error", "标签名称已存在");
            return "redirect:/tags";
        }

        DocumentTag tag = new DocumentTag();
        tag.setName(trimmedName);
        tag.setColor(normalizeColor(color));
        tagService.save(tag);
        redirectAttributes.addFlashAttribute("success", "标签创建成功");
        return "redirect:/tags";
    }

    @PostMapping("/tags/{id}/update")
    public String update(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(defaultValue = "#62c7bd") String color,
            RedirectAttributes redirectAttributes
    ) {
        Optional<DocumentTag> existing = Optional.ofNullable(tagService.getById(id));
        String trimmedName = name == null ? "" : name.trim();
        if (existing.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "标签不存在");
            return "redirect:/tags";
        }
        if (trimmedName.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "标签名称不能为空");
            return "redirect:/tags?editId=" + id;
        }
        boolean duplicated = tagService.exists(Wrappers.<DocumentTag>lambdaQuery()
                .eq(DocumentTag::getName, trimmedName)
                .ne(DocumentTag::getId, id));
        if (duplicated) {
            redirectAttributes.addFlashAttribute("error", "标签名称已存在");
            return "redirect:/tags?editId=" + id;
        }

        DocumentTag tag = existing.get();
        tag.setName(trimmedName);
        tag.setColor(normalizeColor(color));
        tagService.updateById(tag);
        redirectAttributes.addFlashAttribute("success", "标签更新成功");
        return "redirect:/tags";
    }

    @PostMapping("/tags/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        tagRelService.remove(Wrappers.<DocumentTagRel>lambdaQuery().eq(DocumentTagRel::getTagId, id));
        boolean removed = tagService.removeById(id);
        redirectAttributes.addFlashAttribute(removed ? "success" : "error", removed ? "标签删除成功" : "标签不存在");
        return "redirect:/tags";
    }

    private String normalizeColor(String color) {
        if (color == null || color.isBlank()) {
            return "#62c7bd";
        }
        return color.trim();
    }
}
