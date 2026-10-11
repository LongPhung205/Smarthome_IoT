package com.phungvanlong.smarthome.services;

import com.phungvanlong.smarthome.entities.SystemLog;
import com.phungvanlong.smarthome.repositories.SystemLogRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AiLogService {

    private static final Logger log = LoggerFactory.getLogger(AiLogService.class);

    @Autowired
    private SystemLogRepo systemLogRepo;

    @Autowired
    private DeviceService deviceService;

    /**
     * Nhận kết quả từ Python AI Service (Nhận diện khuôn mặt)
     * @param personName Tên người được nhận diện (hoặc "Unknown")
     * @param confidence Độ tin cậy nhận diện (%)
     * @param autoOpenDoor Cờ cho biết có tự động mở cửa hay không
     * @param imageUrl Đường dẫn ảnh người lạ
     */
    public Map<String, Object> logFaceRecognition(String personName, Double confidence, Boolean autoOpenDoor, String imageUrl) {
        Map<String, Object> result = new HashMap<>();

        // Yêu cầu độ tin cậy trên 50.0% mới công nhận là chủ nhà hợp lệ
        boolean isAuthorized = personName != null 
                && !personName.equalsIgnoreCase("unknown") 
                && !personName.equalsIgnoreCase("Người Lạ") 
                && !personName.trim().isEmpty()
                && (confidence != null && confidence >= 50.0);

        String status = isAuthorized ? "SUCCESS" : "DENIED";
        String details = String.format("Nhận diện khuôn mặt: %s (Độ chính xác: %.1f%%)",
                personName, (confidence != null ? confidence : 0.0));

        // Lưu bản ghi vào cơ sở dữ liệu
        SystemLog logEntry = new SystemLog("FACE_RECOGNITION", details, personName, status);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            logEntry.setImageUrl(imageUrl);
        }
        systemLogRepo.save(logEntry);
        log.info("AI Face Recognition Logged: {} ({}%) - {}", personName, confidence, status);

        boolean doorOpened = false;
        if (isAuthorized && Boolean.TRUE.equals(autoOpenDoor)) {
            // Tự động mở cửa bằng DeviceService
            doorOpened = deviceService.setDoor(true, "AI: " + personName);
        } else if (!isAuthorized) {
            log.warn("CẢNH BÁO: Phát hiện người lạ hoặc độ tin cậy dưới 50% trước cửa! (Tên: {}, Độ tin cậy: {}%)", personName, confidence);
        }

        result.put("logged", true);
        result.put("personName", personName);
        result.put("authorized", isAuthorized);
        result.put("doorOpened", doorOpened);
        result.put("message", isAuthorized ? ("Chào mừng " + personName + " trở về nhà!") : "Cảnh báo: Không đủ độ tin cậy (cần >= 50%) hoặc không nhận diện được!");

        return result;
    }
}
