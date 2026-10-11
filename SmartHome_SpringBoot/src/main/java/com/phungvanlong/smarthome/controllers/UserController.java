package com.phungvanlong.smarthome.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phungvanlong.smarthome.services.MqttService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class UserController {

    @Autowired
    private MqttService mqttService;

    @Value("${mqtt.topic.device.control:smarthome/device/control}")
    private String controlTopic;

    // Đường dẫn trỏ trực tiếp tới thư mục dataset của Python AI
    private final String DATASET_DIR = "D:/IoTBTL/SmartHome_AIVision/dataset";

    @GetMapping("/users")
    public String usersPage(Model model) {
        model.addAttribute("mqttConnected", mqttService.isConnected());
        
        List<Map<String, String>> users = new ArrayList<>();
        File folder = new File(DATASET_DIR);
        
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png"));
            if (files != null) {
                for (File file : files) {
                    Map<String, String> user = new HashMap<>();
                    user.put("filename", file.getName());
                    // Bỏ đuôi file và đổi dấu gạch dưới thành dấu cách để hiển thị tên đẹp
                    String name = file.getName().substring(0, file.getName().lastIndexOf('.'));
                    name = name.replace("_", " ").toUpperCase();
                    user.put("name", name);
                    users.add(user);
                }
            }
        }
        
        model.addAttribute("users", users);
        return "users";
    }

    @PostMapping("/api/users/upload")
    public String uploadUserImage(@RequestParam("name") String name, 
                                  @RequestParam("file") MultipartFile file,
                                  RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn một file ảnh!");
            return "redirect:/users";
        }

        try {
            File directory = new File(DATASET_DIR);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Xử lý tên file an toàn (chỉ lấy chữ và số)
            String safeName = name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
            
            // Lấy đuôi mở rộng gốc (.jpg, .png)
            String originalFileName = file.getOriginalFilename();
            String extension = ".jpg";
            if (originalFileName != null && originalFileName.contains(".")) {
                extension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
            
            String newFileName = safeName + extension;
            Path filePath = Paths.get(DATASET_DIR, newFileName);
            
            // Lưu file
            Files.write(filePath, file.getBytes());

            // Gửi lệnh MQTT thông báo cho Python AI nạp lại dữ liệu
            sendReloadCommandToAI();

            redirectAttributes.addFlashAttribute("message", "Đã nạp khuôn mặt mới: " + name + ". Hệ thống AI đang tự động cập nhật!");
        } catch (IOException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Lỗi khi lưu file: " + e.getMessage());
        }

        return "redirect:/users";
    }

    @PostMapping("/api/users/delete/{filename}")
    public String deleteUserImage(@PathVariable String filename, RedirectAttributes redirectAttributes) {
        try {
            Path filePath = Paths.get(DATASET_DIR, filename);
            File file = filePath.toFile();
            
            if (file.exists()) {
                if (file.delete()) {
                    redirectAttributes.addFlashAttribute("message", "Đã xóa khuôn mặt " + filename + " thành công!");
                    sendReloadCommandToAI();
                } else {
                    redirectAttributes.addFlashAttribute("error", "Không thể xóa file " + filename);
                }
            } else {
                redirectAttributes.addFlashAttribute("error", "File không tồn tại!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/users";
    }

    // Phục vụ ảnh trực tiếp ra Web để hiển thị Avatar
    @GetMapping("/api/users/image/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path file = Paths.get(DATASET_DIR).resolve(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                MediaType mediaType = MediaType.IMAGE_JPEG;
                if (filename.toLowerCase().endsWith(".png")) {
                    mediaType = MediaType.IMAGE_PNG;
                }
                return ResponseEntity.ok().contentType(mediaType).body(resource);
            }
        } catch (MalformedURLException e) {
            // Log error
        }
        return ResponseEntity.notFound().build();
    }

    private void sendReloadCommandToAI() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("device", "camera_scan");
            payloadMap.put("action", "RELOAD_DATASET");
            payloadMap.put("timestamp", System.currentTimeMillis());

            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            mqttService.publish(controlTopic, jsonPayload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
