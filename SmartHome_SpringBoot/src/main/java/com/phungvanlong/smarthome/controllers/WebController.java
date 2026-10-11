package com.phungvanlong.smarthome.controllers;

import com.phungvanlong.smarthome.entities.SensorData;
import com.phungvanlong.smarthome.entities.SystemLog;
import com.phungvanlong.smarthome.repositories.SensorDataRepo;
import com.phungvanlong.smarthome.repositories.SystemLogRepo;
import com.phungvanlong.smarthome.services.DeviceService;
import com.phungvanlong.smarthome.services.MqttService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class WebController {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private SensorDataRepo sensorDataRepo;

    @Autowired
    private SystemLogRepo systemLogRepo;

    @Autowired
    private MqttService mqttService;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        // Lấy dữ liệu cảm biến mới nhất
        SensorData latestSensor = sensorDataRepo.findTopByOrderByRecordedAtDesc()
                .orElse(new SensorData(28.0, 65.0, 120.0, false));
        model.addAttribute("latestSensor", latestSensor);

        // Trạng thái các thiết bị (đèn, quạt, cửa, còi)
        model.addAttribute("deviceStates", deviceService.getDeviceStates());

        // Lịch sử 10 hoạt động gần nhất
        List<SystemLog> recentLogs = systemLogRepo.findTop50ByOrderByTimestampDesc();
        if (recentLogs.size() > 10) {
            recentLogs = recentLogs.subList(0, 10);
        }
        model.addAttribute("recentLogs", recentLogs);

        // Trạng thái kết nối MQTT
        model.addAttribute("mqttConnected", mqttService.isConnected());

        return "dashboard";
    }

    @GetMapping("/history")
    public String history(Model model) {
        // Toàn bộ lịch sử cảm biến gần nhất
        List<SensorData> sensorList = sensorDataRepo.findTop50ByOrderByRecordedAtDesc();
        model.addAttribute("sensorList", sensorList);

        // Toàn bộ nhật ký hệ thống gần nhất
        List<SystemLog> logs = systemLogRepo.findTop50ByOrderByTimestampDesc();
        model.addAttribute("logs", logs);

        return "history";
    }

    @GetMapping("/intruders")
    public String intruders(Model model) {
        List<SystemLog> intrudersList = systemLogRepo.findByEventTypeAndStatusAndImageUrlIsNotNullOrderByTimestampDesc("FACE_RECOGNITION", "DENIED");
        model.addAttribute("intrudersList", intrudersList);
        return "intruders";
    }

    @GetMapping("/schedules")
    public String schedules(Model model) {
        return "schedules";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
