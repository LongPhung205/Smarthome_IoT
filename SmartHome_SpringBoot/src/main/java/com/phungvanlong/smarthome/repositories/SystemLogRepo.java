package com.phungvanlong.smarthome.repositories;

import com.phungvanlong.smarthome.entities.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemLogRepo extends JpaRepository<SystemLog, Long> {

    // Lấy danh sách lịch sử sắp xếp theo thời gian mới nhất
    List<SystemLog> findAllByOrderByTimestampDesc();

    // Lấy top 50 sự kiện gần nhất cho trang Dashboard / History
    List<SystemLog> findTop50ByOrderByTimestampDesc();

    // Tìm kiếm theo loại sự kiện (DOOR_OPEN, FACE_RECOGNITION,...)
    List<SystemLog> findByEventTypeOrderByTimestampDesc(String eventType);

    // Tìm kiếm người lạ (có ảnh)
    List<SystemLog> findByEventTypeAndStatusAndImageUrlIsNotNullOrderByTimestampDesc(String eventType, String status);
}
