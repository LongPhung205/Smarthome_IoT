package com.phungvanlong.smarthome.repositories;

import com.phungvanlong.smarthome.entities.DeviceSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceScheduleRepo extends JpaRepository<DeviceSchedule, Long> {
    List<DeviceSchedule> findByIsActiveTrue();
    List<DeviceSchedule> findByScheduleTimeAndIsActiveTrue(String scheduleTime);
}
