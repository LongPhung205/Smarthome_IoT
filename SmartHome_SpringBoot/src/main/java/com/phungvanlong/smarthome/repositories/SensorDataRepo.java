package com.phungvanlong.smarthome.repositories;

import com.phungvanlong.smarthome.entities.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SensorDataRepo extends JpaRepository<SensorData, Long> {

    // Lấy bản ghi cảm biến mới nhất
    Optional<SensorData> findTopByOrderByRecordedAtDesc();

    // Lấy danh sách 20 hoặc 50 bản ghi gần nhất để vẽ biểu đồ
    List<SensorData> findTop20ByOrderByRecordedAtDesc();

    List<SensorData> findTop50ByOrderByRecordedAtDesc();
}
