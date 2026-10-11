package com.phungvanlong.smarthome.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password; // Dùng cho đăng nhập Web

    @Column(nullable = false)
    private String pinHash; // Dùng cho bảo mật lớp 2 (Mở cửa)

    @Column(nullable = false)
    private String role; // ROLE_ADMIN, ROLE_USER

    @Column
    private String fullName;

    @Column
    private String email;

    private LocalDateTime createdAt;

    public UserAccount() {
        this.createdAt = LocalDateTime.now();
    }

    public UserAccount(String username, String password, String pinHash, String role, String fullName) {
        this.username = username;
        this.password = password;
        this.pinHash = pinHash;
        this.role = role;
        this.fullName = fullName;
        this.createdAt = LocalDateTime.now();
    }

    public UserAccount(String username, String password, String pinHash, String role, String fullName, String email) {
        this.username = username;
        this.password = password;
        this.pinHash = pinHash;
        this.role = role;
        this.fullName = fullName;
        this.email = email;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
