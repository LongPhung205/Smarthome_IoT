package com.phungvanlong.smarthome.services;

import com.phungvanlong.smarthome.entities.UserAccount;
import com.phungvanlong.smarthome.repositories.UserAccountRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private UserAccountRepo userAccountRepo;

    @Value("${spring.mail.username:phungvanlong10925@gmail.com}")
    private String fromEmail;

    /**
     * Gửi cảnh báo khẩn cấp tới tất cả các tài khoản ROLE_ADMIN có cấu hình email
     */
    public void sendAlertEmail(String subject, String text) {
        try {
            List<String> adminEmails = userAccountRepo.findByRole("ROLE_ADMIN").stream()
                    .map(UserAccount::getEmail)
                    .filter(email -> email != null && !email.trim().isEmpty())
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());

            if (adminEmails.isEmpty()) {
                log.warn("[EMAIL] Chưa có tài khoản Quản trị viên (ROLE_ADMIN) nào được thiết lập Email để nhận cảnh báo!");
                return;
            }

            for (String recipient : adminEmails) {
                try {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(fromEmail);
                    message.setTo(recipient);
                    message.setSubject(subject);
                    message.setText(text);
                    mailSender.send(message);
                    log.info("[EMAIL] Gửi cảnh báo thành công tới Quản trị viên: {}", recipient);
                } catch (Exception e) {
                    log.error("[EMAIL ERROR] Lỗi khi gửi email tới {}: {}", recipient, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[EMAIL ERROR] Lỗi trong quá trình xử lý gửi email cảnh báo: {}", e.getMessage(), e);
        }
    }

    /**
     * Gửi email tới một địa chỉ cụ thể
     */
    public boolean sendDirectEmail(String toEmail, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("[EMAIL] Gửi email trực tiếp thành công tới: {}", toEmail);
            return true;
        } catch (Exception e) {
            log.error("[EMAIL ERROR] Lỗi khi gửi email trực tiếp tới {}: {}", toEmail, e.getMessage());
            return false;
        }
    }
}
