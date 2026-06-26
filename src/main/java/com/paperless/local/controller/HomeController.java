package com.paperless.local.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "项目首页");
        model.addAttribute("documentTotal", 0);
        model.addAttribute("todayUploads", 0);
        model.addAttribute("categoryTotal", 0);
        model.addAttribute("tagTotal", 0);
        return "dashboard";
    }
}
