package com.phungvanlong.smarthome.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_data")
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "humidity")
    private Double humidity;

    @Column(name = "gas_level")
    private Double gasLevel; // Chỉ số khí ga MQ2

    @Column(name = "flame_detected")
    private Boolean flameDetected; // Cảnh báo lửa (nếu có)

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    public SensorData() {
        this.recordedAt = LocalDateTime.now();
    }

    public SensorData(Double temperature, Double humidity, Double gasLevel, Boolean flameDetected) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.gasLevel = gasLevel;
        this.flameDetected = flameDetected;
        this.recordedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.recordedAt == null) {
            this.recordedAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getHumidity() {
        return humidity;
    }

    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }

    public Double getGasLevel() {
        return gasLevel;
    }

    public void setGasLevel(Double gasLevel) {
        this.gasLevel = gasLevel;
    }

    public Boolean getFlameDetected() {
        return flameDetected;
    }

    public void setFlameDetected(Boolean flameDetected) {
        this.flameDetected = flameDetected;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }
}
