package com.phungvanlong.smarthome.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phungvanlong.smarthome.entities.SystemLog;
import com.phungvanlong.smarthome.repositories.SystemLogRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    @Autowired
    private MqttService mqttService;

    @Autowired
    private SystemLogRepo systemLogRepo;

    @Value("${mqtt.topic.device.control:smarthome/device/control}")
    private String controlTopic;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Lưu trạng thái hiện tại của các thiết bị (In-memory cache)
    private final Map<String, Boolean> deviceStates = new ConcurrentHashMap<>();

    public DeviceService() {
        // Khởi tạo trạng thái mặc định: tất cả đang tắt / cửa đang đóng
        deviceStates.put("light", false);
        deviceStates.put("fan", false);
        deviceStates.put("door", false); // false = closed, true = open
        deviceStates.put("alarm", false);
    }

    public Map<String, Boolean> getDeviceStates() {
        return new HashMap<>(deviceStates);
    }

    public boolean setLight(boolean state, String triggerSource) {
        return controlDevice("light", state, triggerSource);
    }

    public boolean setFan(boolean state, String triggerSource) {
        return controlDevice("fan", state, triggerSource);
    }

    public boolean setDoor(boolean open, String triggerSource) {
        boolean success = controlDevice("door", open, triggerSource);
        if (success && open) {
            // Nếu là mở cửa, ghi log chi tiết
            systemLogRepo.save(new SystemLog("DOOR_OPEN", "Cửa chính được mở bởi: " + triggerSource, triggerSource, "SUCCESS"));
        } else if (success && !open) {
            systemLogRepo.save(new SystemLog("DOOR_CLOSE", "Cửa chính đã đóng lại (" + triggerSource + ")", triggerSource, "SUCCESS"));
        }
        return success;
    }

    public boolean setAlarm(boolean state, String triggerSource) {
        return controlDevice("alarm", state, triggerSource);
    }

    public synchronized boolean controlDevice(String deviceName, boolean state, String triggerSource) {
        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("device", deviceName.toLowerCase());
            payloadMap.put("action", state ? "ON" : "OFF");
            payloadMap.put("source", triggerSource);
            payloadMap.put("timestamp", System.currentTimeMillis());

            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            boolean sent = mqttService.publish(controlTopic, jsonPayload);

            // Cập nhật trạng thái bộ nhớ
            deviceStates.put(deviceName.toLowerCase(), state);

            // Ghi nhật ký hệ thống
            String actionDesc = (state ? "Bật " : "Tắt ") + deviceName;
            if (deviceName.equalsIgnoreCase("door")) {
                actionDesc = (state ? "Mở " : "Đóng ") + "cửa";
            }
            systemLogRepo.save(new SystemLog("DEVICE_CONTROL", actionDesc + " bởi " + triggerSource, triggerSource, sent ? "SUCCESS" : "MQTT_OFFLINE"));

            log.info("Điều khiển thiết bị [{}] -> {} (Trigger: {})", deviceName, state ? "ON" : "OFF", triggerSource);
            return true;
        } catch (Exception e) {
            log.error("Lỗi khi điều khiển thiết bị {}: {}", deviceName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Xử lý câu lệnh tự nhiên (Voice Recognition) từ Web hoặc Mobile
     */
    public Map<String, Object> processVoiceCommand(String rawCommand, String user) {
        Map<String, Object> response = new HashMap<>();
        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Lệnh trống hoặc không nhận dạng được âm thanh!");
            return response;
        }

        String cmd = rawCommand.toLowerCase().trim();
        String replyMessage;
        boolean handled = false;

        if (cmd.contains("bật đèn") || cmd.contains("mở đèn") || cmd.contains("turn on light")) {
            setLight(true, "Voice (" + user + ")");
            replyMessage = "Đã bật đèn chiếu sáng!";
            handled = true;
        } else if (cmd.contains("tắt đèn") || cmd.contains("turn off light")) {
            setLight(false, "Voice (" + user + ")");
            replyMessage = "Đã tắt đèn chiếu sáng!";
            handled = true;
        } else if (cmd.contains("bật quạt") || cmd.contains("mở quạt") || cmd.contains("turn on fan")) {
            setFan(true, "Voice (" + user + ")");
            replyMessage = "Đã bật quạt mát!";
            handled = true;
        } else if (cmd.contains("tắt quạt") || cmd.contains("turn off fan")) {
            setFan(false, "Voice (" + user + ")");
            replyMessage = "Đã tắt quạt!";
            handled = true;
        } else if (cmd.contains("mở cửa") || cmd.contains("open door")) {
            // Khóa tính năng mở cửa bằng giọng nói vì lý do an ninh
            replyMessage = "Xin lỗi, tính năng mở cửa bằng giọng nói đã bị khóa để đảm bảo an ninh!";
            handled = true;
        } else if (cmd.contains("đóng cửa") || cmd.contains("close door")) {
            setDoor(false, "Voice (" + user + ")");
            replyMessage = "Đã gửi lệnh đóng cửa!";
            handled = true;
        } else if (cmd.contains("báo động") || cmd.contains("bật còi") || cmd.contains("cứu") || cmd.contains("cháy")) {
            setAlarm(true, "Voice (" + user + ")");
            replyMessage = "ĐÃ KÍCH HOẠT CHẾ ĐỘ BÁO ĐỘNG!";
            handled = true;
        } else if (cmd.contains("tắt báo động") || cmd.contains("tắt còi") || cmd.contains("hủy báo động")) {
            setAlarm(false, "Voice (" + user + ")");
            replyMessage = "Đã tắt chuông báo động!";
            handled = true;
        } else {
            replyMessage = "Không hiểu lệnh: \"" + rawCommand + "\". Vui lòng thử lại!";
        }

        // Lưu vết lệnh giọng nói vào log
        systemLogRepo.save(new SystemLog("VOICE_COMMAND", "Nội dung: \"" + rawCommand + "\" -> " + replyMessage, user, handled ? "SUCCESS" : "UNRECOGNIZED"));

        response.put("success", handled);
        response.put("command", rawCommand);
        response.put("reply", replyMessage);
        response.put("states", getDeviceStates());
        return response;
    }
}
