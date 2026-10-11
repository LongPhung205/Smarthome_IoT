package com.phungvanlong.smarthome.controllers;

import com.phungvanlong.smarthome.entities.SensorData;
import com.phungvanlong.smarthome.entities.SystemLog;
import com.phungvanlong.smarthome.repositories.SensorDataRepo;
import com.phungvanlong.smarthome.repositories.SystemLogRepo;
import com.phungvanlong.smarthome.services.AiLogService;
import com.phungvanlong.smarthome.services.AuthService;
import com.phungvanlong.smarthome.services.DeviceService;
import com.phungvanlong.smarthome.services.MqttService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private AuthService authService;

    @Autowired
    private AiLogService aiLogService;

    @Autowired
    private SensorDataRepo sensorDataRepo;

    @Autowired
    private SystemLogRepo systemLogRepo;

    @Autowired
    private MqttService mqttService;

    /**
     * API nhận lệnh giọng nói từ Web Speech API hoặc App Mobile
     * Body: { "command": "bật đèn", "user": "Admin" }
     */
    @PostMapping("/voice")
    public ResponseEntity<Map<String, Object>> handleVoiceCommand(@RequestBody Map<String, String> request) {
        String command = request.getOrDefault("command", "");
        String user = request.getOrDefault("user", "WebUser");

        Map<String, Object> result = deviceService.processVoiceCommand(command, user);
        return ResponseEntity.ok(result);
    }

    /**
     * API điều khiển thiết bị trực tiếp từ giao diện Web Toggle Button
     * Body: { "device": "light", "state": true, "source": "Web Switch" }
     */
    @PostMapping("/device/control")
    public ResponseEntity<Map<String, Object>> controlDevice(@RequestBody Map<String, Object> request) {
        String device = (String) request.get("device");
        Boolean state = Boolean.parseBoolean(String.valueOf(request.get("state")));
        String source = (String) request.getOrDefault("source", "Web Dashboard");

        boolean success = deviceService.controlDevice(device, state, source);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("device", device);
        response.put("state", state);
        response.put("allStates", deviceService.getDeviceStates());
        return ResponseEntity.ok(response);
    }

    /**
     * API điều khiển thiết bị yêu cầu xác thực bằng mã PIN (Lớp bảo mật 2)
     */
    @PostMapping("/device/control-secure")
    public ResponseEntity<Map<String, Object>> controlDeviceSecure(@RequestBody Map<String, Object> request) {
        String device = (String) request.get("device");
        Boolean state = Boolean.parseBoolean(String.valueOf(request.get("state")));
        String source = (String) request.getOrDefault("source", "Web Dashboard Secure");
        String pin = (String) request.get("pin");

        Map<String, Object> response = new HashMap<>();

        // Kiểm tra mã PIN (mặc định kiểm tra cho user 'admin')
        if (!authService.verifyPin("admin", pin)) {
            response.put("success", false);
            response.put("message", "Mã PIN không chính xác!");
            return ResponseEntity.status(401).body(response);
        }

        // Nếu mã PIN đúng, thực thi lệnh
        boolean success = deviceService.controlDevice(device, state, source);
        response.put("success", success);
        response.put("device", device);
        response.put("state", state);
        response.put("allStates", deviceService.getDeviceStates());
        return ResponseEntity.ok(response);
    }

    /**
     * API lấy trạng thái hiện tại của tất cả các thiết bị
     */
    @GetMapping("/device/states")
    public ResponseEntity<Map<String, Boolean>> getDeviceStates() {
        return ResponseEntity.ok(deviceService.getDeviceStates());
    }

    /**
     * API dành cho Python AI Service gọi khi nhận diện khuôn mặt
     * Body: { "personName": "Phùng Văn Long", "confidence": 98.2, "autoOpenDoor": true }
     */
    @PostMapping("/ai/face-log")
    public ResponseEntity<Map<String, Object>> handleFaceRecognition(@RequestBody Map<String, Object> request) {
        String personName = (String) request.getOrDefault("personName", "Unknown");
        Double confidence = request.get("confidence") != null ? Double.valueOf(request.get("confidence").toString()) : 0.0;
        Boolean autoOpenDoor = request.get("autoOpenDoor") != null ? Boolean.valueOf(request.get("autoOpenDoor").toString()) : true;
        String imageUrl = (String) request.get("imageUrl");

        Map<String, Object> result = aiLogService.logFaceRecognition(personName, confidence, autoOpenDoor, imageUrl);
        return ResponseEntity.ok(result);
    }

    /**
     * API lấy dữ liệu cảm biến mới nhất (phục vụ AJAX polling trên Dashboard)
     */
    @GetMapping("/sensor/latest")
    public ResponseEntity<SensorData> getLatestSensorData() {
        return sensorDataRepo.findTopByOrderByRecordedAtDesc()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * API lấy lịch sử cảm biến gần nhất để vẽ biểu đồ Chart.js
     */
    @GetMapping("/sensor/history")
    public ResponseEntity<List<SensorData>> getSensorHistory(@RequestParam(defaultValue = "20") int limit) {
        List<SensorData> history = sensorDataRepo.findTop20ByOrderByRecordedAtDesc();
        return ResponseEntity.ok(history);
    }

    /**
     * API lấy danh sách log hệ thống gần nhất
     */
    @GetMapping("/logs")
    public ResponseEntity<List<SystemLog>> getRecentLogs() {
        return ResponseEntity.ok(systemLogRepo.findTop50ByOrderByTimestampDesc());
    }

    /**
     * API kiểm tra trạng thái kết nối MQTT
     */
    @GetMapping("/mqtt/status")
    public ResponseEntity<Map<String, Object>> getMqttStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", mqttService.isConnected());
        return ResponseEntity.ok(status);
    }

    /**
     * API giả lập dữ liệu cảm biến (hữu ích để test khi chưa có ESP32 thật)
     */
    @PostMapping("/sensor/simulate")
    public ResponseEntity<SensorData> simulateSensorData(@RequestBody SensorData dummy) {
        SensorData saved = sensorDataRepo.save(dummy);
        return ResponseEntity.ok(saved);
    }

    /**
     * API xóa nhật ký người lạ
     */
    @DeleteMapping("/intruders/{id}")
    public ResponseEntity<Map<String, Object>> deleteIntruderLog(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (systemLogRepo.existsById(id)) {
                systemLogRepo.deleteById(id);

                // Lấy tên người dùng hiện tại đang đăng nhập
                String currentUser = "Unknown User";
                if (SecurityContextHolder.getContext().getAuthentication() != null) {
                    currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
                }

                // Ghi log lại việc xóa
                String details = "Đã xóa ảnh/nhật ký người lạ có ID: " + id;
                SystemLog deleteLog = new SystemLog("DELETE_LOG", details, currentUser, "SUCCESS");
                systemLogRepo.save(deleteLog);

                response.put("success", true);
                response.put("message", "Đã xóa thành công");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Không tìm thấy bản ghi");
                return ResponseEntity.status(404).body(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
