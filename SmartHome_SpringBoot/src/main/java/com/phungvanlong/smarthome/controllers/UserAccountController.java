package com.phungvanlong.smarthome.controllers;

import com.phungvanlong.smarthome.entities.UserAccount;
import com.phungvanlong.smarthome.repositories.UserAccountRepo;
import com.phungvanlong.smarthome.services.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class UserAccountController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserAccountRepo userAccountRepo;

    @GetMapping("/{id}")
    public ResponseEntity<?> getAccount(@PathVariable Long id) {
        Optional<UserAccount> userOpt = userAccountRepo.findById(id);
        if (userOpt.isPresent()) {
            // Do not return password and pinHash to frontend
            UserAccount u = userOpt.get();
            u.setPassword(null);
            u.setPinHash(null);
            return ResponseEntity.ok(u);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody UserAccount user) {
        try {
            UserAccount saved = authService.createUser(user);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAccount(@PathVariable Long id, @RequestBody UserAccount user) {
        try {
            UserAccount updated = authService.updateUser(id, user);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@PathVariable Long id) {
        try {
            authService.deleteUser(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
