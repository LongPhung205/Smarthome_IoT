package com.phungvanlong.smarthome.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.client.id:smarthome_spring_backend}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);

        if (username != null && !username.trim().isEmpty()) {
            options.setUserName(username);
        }
        if (password != null && !password.trim().isEmpty()) {
            options.setPassword(password.toCharArray());
        }
        return options;
    }

    @Bean
    public MqttClient mqttClient(MqttConnectOptions options) {
        try {
            // Thêm hậu tố timestamp để tránh trùng clientId khi khởi động lại
            String uniqueClientId = clientId + "_" + System.currentTimeMillis();
            MqttClient client = new MqttClient(brokerUrl, uniqueClientId, new MemoryPersistence());
            log.info("MqttClient bean created for broker: {} with clientId: {}", brokerUrl, uniqueClientId);
            return client;
        } catch (MqttException e) {
            log.error("Lỗi khi khởi tạo MqttClient: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo MqttClient", e);
        }
    }
}
