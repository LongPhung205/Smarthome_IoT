package com.phungvanlong.smarthome.controllers;

import com.phungvanlong.smarthome.entities.UserAccount;
import com.phungvanlong.smarthome.repositories.UserAccountRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UserAccountRepo userAccountRepo;

    @GetMapping
    public String usersPage(Model model) {
        List<UserAccount> users = userAccountRepo.findAll();
        model.addAttribute("users", users);
        return "admin_users";
    }
}
