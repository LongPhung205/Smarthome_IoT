package com.phungvanlong.smarthome.services;

import com.phungvanlong.smarthome.entities.DeviceSchedule;
import com.phungvanlong.smarthome.repositories.DeviceScheduleRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    @Autowired
    private DeviceScheduleRepo scheduleRepo;

    @Autowired
    private DeviceService deviceService;

    // Chạy mỗi phút tại giây thứ 00
    @Scheduled(cron = "0 * * * * *")
    public void executeSchedules() {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        List<DeviceSchedule> dueSchedules = scheduleRepo.findByScheduleTimeAndIsActiveTrue(currentTime);

        if (!dueSchedules.isEmpty()) {
            log.info("Phát hiện {} lịch hẹn cần thực thi vào lúc {}", dueSchedules.size(), currentTime);
            for (DeviceSchedule schedule : dueSchedules) {
                boolean targetState = "ON".equalsIgnoreCase(schedule.getAction());
                log.info("Thực thi lịch hẹn: Tên thiết bị='{}', Hành động='{}'", schedule.getDeviceName(), schedule.getAction());
                deviceService.controlDevice(schedule.getDeviceName(), targetState, "HỆ THỐNG HẸN GIỜ");
                
                // Lịch trình ngày tháng năm cụ thể nên chỉ chạy 1 lần. Chạy xong thì vô hiệu hóa.
                schedule.setIsActive(false);
                scheduleRepo.save(schedule);
            }
        }
    }
}
