package com.phungvanlong.smarthome.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phungvanlong.smarthome.entities.SensorData;
import com.phungvanlong.smarthome.entities.SystemLog;
import com.phungvanlong.smarthome.repositories.SensorDataRepo;
import com.phungvanlong.smarthome.repositories.SystemLogRepo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class MqttService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttService.class);

    @Autowired
    @Lazy
    private DeviceService deviceService;

    @Autowired
    private MqttClient mqttClient;

    @Autowired
    private MqttConnectOptions connectOptions;

    @Autowired
    private SensorDataRepo sensorDataRepo;

    @Autowired
    private SystemLogRepo systemLogRepo;

    @Autowired
    private EmailService emailService;

    @Value("${mqtt.topic.sensor:smarthome/sensor}")
    private String sensorTopic;

    @Value("${mqtt.topic.device.status:smarthome/device/status}")
    private String statusTopic;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        mqttClient.setCallback(this);
        connectToBroker();
    }

    public synchronized void connectToBroker() {
        new Thread(() -> {
            try {
                if (!mqttClient.isConnected()) {
                    log.info("Đang kết nối tới MQTT Broker: {}", mqttClient.getServerURI());
                    mqttClient.connect(connectOptions);
                    log.info("Kết nối MQTT thành công!");
                }
            } catch (MqttException e) {
                log.warn("Chưa thể kết nối tới MQTT Broker ({}) lúc khởi động: {}. Sẽ tự động kết nối lại khi Broker khả dụng.",
                        mqttClient.getServerURI(), e.getMessage());
            }
        }).start();
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT đã sẵn sàng (Reconnect: {}). Bắt đầu đăng ký lắng nghe topics...", reconnect);
        try {
            mqttClient.subscribe(sensorTopic, 1);
            mqttClient.subscribe(statusTopic, 1);
            log.info("Đã subscribe thành công topic cảm biến [{}] và topic trạng thái [{}]", sensorTopic, statusTopic);
        } catch (MqttException e) {
            log.error("Lỗi khi subscribe topic: {}", e.getMessage(), e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Mất kết nối tới MQTT Broker: {}", (cause != null ? cause.getMessage() : "Không rõ nguyên nhân"));
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.info("Nhận bản tin MQTT từ topic [{}]: {}", topic, payload);

        try {
            if (topic.equals(sensorTopic)) {
                handleSensorMessage(payload);
            } else if (topic.equals(statusTopic)) {
                handleStatusMessage(payload);
            }
        } catch (Exception e) {
            log.error("Lỗi khi xử lý bản tin MQTT: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Hoàn thành gửi message
    }

    /**
     * Xử lý dữ liệu cảm biến từ ESP32 gửi lên
     * Định dạng JSON mẫu: {"temperature": 29.5, "humidity": 68.0, "gasLevel": 150.0, "flame": false}
     */
    private void handleSensorMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            Double temp = root.has("temperature") ? root.get("temperature").asDouble() : null;
            Double hum = root.has("humidity") ? root.get("humidity").asDouble() : null;
            Double gas = root.has("gasLevel") ? root.get("gasLevel").asDouble() : null;

            boolean flame = (root.has("flame") && root.get("flame").asBoolean()) 
                         || (root.has("ir") && root.get("ir").asBoolean());

            SensorData data = new SensorData(temp, hum, gas, flame);
            sensorDataRepo.save(data);
            log.info("Đã lưu dữ liệu cảm biến vào Database: Temp={}°C, Hum={}% Gas={} Flame={}", temp, hum, gas, flame);

            // Kiểm tra ngưỡng cảnh báo khí gas
            if (gas != null && gas > 400.0) {
                String warningText = String.format("CẢNH BÁO NGUY HIỂM: Rò rỉ khí gas! (Gas=%s PPM)", gas);
                log.warn(warningText);
                systemLogRepo.save(new SystemLog("ALARM", warningText, "ESP32_SENSOR", "WARNING"));

                // Gửi Email cảnh báo
                emailService.sendAlertEmail("CẢNH BÁO KHẨN CẤP: RÒ RỈ KHÍ GAS - Smart Home", warningText + "\nThời gian: " + java.time.LocalDateTime.now());

                // Chế độ báo cháy thông minh (Auto-Mitigation)
                log.info(">>> KÍCH HOẠT HỆ THỐNG AN TOÀN AUTO-MITIGATION <<<");
                if (deviceService != null) {
                    deviceService.setFan(true, "AUTO-GAS-ALARM");
                    deviceService.setDoor(true, "AUTO-GAS-ALARM");
                    deviceService.setAlarm(true, "AUTO-GAS-ALARM");
                }
            }

            // Kiểm tra cảnh báo Lửa / Hồng ngoại
            if (flame) {
                String flameText = "CẢNH BÁO NGUY HIỂM: Phát hiện bức xạ lửa / hồng ngoại bất thường tại nhà!";
                log.warn(flameText);
                systemLogRepo.save(new SystemLog("ALARM", flameText, "ESP32_SENSOR", "DANGER"));

                // Gửi Email cảnh báo lửa
                emailService.sendAlertEmail("CẢNH BÁO KHẨN CẤP: PHÁT HIỆN LỬA - Smart Home", flameText + "\nThời gian: " + java.time.LocalDateTime.now());

                if (deviceService != null) {
                    deviceService.setFan(true, "AUTO-FLAME-ALARM");
                    deviceService.setDoor(true, "AUTO-FLAME-ALARM");
                    deviceService.setAlarm(true, "AUTO-FLAME-ALARM");
                }
            }
        } catch (Exception e) {
            log.error("Không thể phân tích dữ liệu JSON cảm biến: {}", payload, e);
        }
    }

    private void handleStatusMessage(String payload) {
        log.info("ESP32 phản hồi trạng thái: {}", payload);
    }

    /**
     * Hàm publish lệnh MQTT xuống ESP32
     */
    public boolean publish(String topic, String payload) {
        try {
            if (!mqttClient.isConnected()) {
                log.warn("MqttClient chưa kết nối, không thể gửi tin tới topic [{}]", topic);
                return false;
            }
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            mqttClient.publish(topic, msg);
            log.info("Đã publish MQTT tới topic [{}]: {}", topic, payload);
            return true;
        } catch (MqttException e) {
            log.error("Lỗi khi publish MQTT tới topic [{}]: {}", topic, e.getMessage(), e);
            return false;
        }
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("Đã đóng kết nối MQTT.");
            }
        } catch (MqttException e) {
            log.error("Lỗi khi đóng kết nối MQTT: {}", e.getMessage());
        }
    }
}
