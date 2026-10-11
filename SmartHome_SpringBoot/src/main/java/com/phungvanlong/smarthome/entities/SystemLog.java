package com.phungvanlong.smarthome.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_logs")
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType; // DOOR_OPEN, DOOR_CLOSE, FACE_RECOGNITION, VOICE_COMMAND, ALARM, DEVICE_CONTROL

    @Column(name = "details", length = 500)
    private String details; // Nội dung chi tiết: "Cửa mở lúc mấy giờ", "Nhận diện khuôn mặt thành công"

    @Column(name = "person_name")
    private String personName; // Ai đã vào / Ai đã ra

    @Column(name = "status")
    private String status; // SUCCESS, WARNING, DENIED, FAILED

    @Column(name = "image_url")
    private String imageUrl; // Chứa URL hình ảnh nếu phát hiện người lạ

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    public SystemLog() {
        this.timestamp = LocalDateTime.now();
    }

    public SystemLog(String eventType, String details, String personName, String status) {
        this.eventType = eventType;
        this.details = details;
        this.personName = personName;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
