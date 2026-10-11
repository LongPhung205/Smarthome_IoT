package com.phungvanlong.smarthome.services;

import com.phungvanlong.smarthome.entities.UserAccount;
import com.phungvanlong.smarthome.repositories.UserAccountRepo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserAccountRepo userAccountRepo;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @PostConstruct
    public void initDefaultUser() {
        if (userAccountRepo.count() == 0) {
            String defaultPin = "1234";
            String adminPass = "admin123";
            String guestPass = "guest123";

            String hashedPin = passwordEncoder.encode(defaultPin);
            
            UserAccount admin = new UserAccount("admin", passwordEncoder.encode(adminPass), hashedPin, "ROLE_ADMIN", "Quản Trị Viên", "phungvanlong10925@gmail.com");
            UserAccount guest = new UserAccount("guest", passwordEncoder.encode(guestPass), hashedPin, "ROLE_USER", "Khách", null);
            
            userAccountRepo.save(admin);
            userAccountRepo.save(guest);
            log.info("Đã tạo 2 tài khoản mặc định (admin/admin123) và (guest/guest123) với mã PIN chung là 1234");
        }
    }

    public boolean verifyPin(String username, String rawPin) {
        Optional<UserAccount> userOpt = userAccountRepo.findByUsername(username);
        if (userOpt.isPresent()) {
            return passwordEncoder.matches(rawPin, userOpt.get().getPinHash());
        }
        return false;
    }

    public UserAccount createUser(UserAccount user) {
        if (userAccountRepo.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại!");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // Note: Client should send raw pin in pinHash field when creating user
        user.setPinHash(passwordEncoder.encode(user.getPinHash())); 
        return userAccountRepo.save(user);
    }

    public UserAccount updateUser(Long id, UserAccount updateInfo) {
        Optional<UserAccount> opt = userAccountRepo.findById(id);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Tài khoản không tồn tại!");
        }
        UserAccount existing = opt.get();
        existing.setFullName(updateInfo.getFullName());
        existing.setRole(updateInfo.getRole());
        existing.setEmail(updateInfo.getEmail());
        
        // Update password if provided
        if (updateInfo.getPassword() != null && !updateInfo.getPassword().trim().isEmpty()) {
            existing.setPassword(passwordEncoder.encode(updateInfo.getPassword()));
        }
        
        // Update PIN if provided (sent via pinHash field)
        if (updateInfo.getPinHash() != null && !updateInfo.getPinHash().trim().isEmpty()) {
            existing.setPinHash(passwordEncoder.encode(updateInfo.getPinHash()));
        }
        
        return userAccountRepo.save(existing);
    }

    public void deleteUser(Long id) {
        Optional<UserAccount> opt = userAccountRepo.findById(id);
        if (opt.isPresent()) {
            UserAccount user = opt.get();
            if ("admin".equalsIgnoreCase(user.getUsername())) {
                throw new IllegalArgumentException("Không thể xóa tài khoản admin gốc!");
            }
            userAccountRepo.delete(user);
        }
    }
}
