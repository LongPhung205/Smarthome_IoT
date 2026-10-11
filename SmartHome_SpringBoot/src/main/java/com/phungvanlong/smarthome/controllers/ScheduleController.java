package com.phungvanlong.smarthome.controllers;

import com.phungvanlong.smarthome.entities.DeviceSchedule;
import com.phungvanlong.smarthome.repositories.DeviceScheduleRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    @Autowired
    private DeviceScheduleRepo scheduleRepo;

    // Lấy toàn bộ lịch hẹn
    @GetMapping
    public ResponseEntity<List<DeviceSchedule>> getAllSchedules() {
        return ResponseEntity.ok(scheduleRepo.findAll());
    }

    // Thêm mới lịch hẹn
    @PostMapping
    public ResponseEntity<?> createSchedule(@RequestBody DeviceSchedule schedule) {
        if (schedule.getDeviceName() == null || schedule.getAction() == null || schedule.getScheduleTime() == null) {
            return ResponseEntity.badRequest().body("Thiếu thông tin thiết bị, hành động hoặc thời gian!");
        }
        DeviceSchedule saved = scheduleRepo.save(schedule);
        return ResponseEntity.ok(saved);
    }

    // Xóa lịch hẹn
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSchedule(@PathVariable Long id) {
        if (!scheduleRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        scheduleRepo.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // Bật/tắt trạng thái lịch hẹn
    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggleSchedule(@PathVariable Long id) {
        Optional<DeviceSchedule> optional = scheduleRepo.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        DeviceSchedule schedule = optional.get();
        schedule.setIsActive(!schedule.getIsActive());
        scheduleRepo.save(schedule);
        return ResponseEntity.ok(schedule);
    }
}
