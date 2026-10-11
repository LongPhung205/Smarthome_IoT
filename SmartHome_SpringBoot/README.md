# SmartHome IoT - Hệ Thống Điều Khiển & Giám Sát Nhà Thông Minh

Hệ sinh thái SmartHome gồm 3 thành phần chính:
1. **Spring Boot (Backend):** Máy chủ trung tâm lưu trữ CSDL MySQL, giao diện Web (Thymeleaf), và xử lý luồng MQTT/API.
2. **ESP32 (Thiết bị IoT):** Code bằng Arduino IDE (C++) thu thập dữ liệu (DHT, MQ-2, Lửa) và điều khiển relay, động cơ servo thông qua MQTT.
3. **AI Vision (Python):** Ứng dụng chạy trên máy tính sử dụng OpenCV & `face_recognition` nhận diện khuôn mặt qua WebCam, tự động mở cửa bằng REST API.

---

## 1. Cấu Trúc Tổng Thể (Toàn Bộ Dự Án)

```
IoTBTL/
├── SmartHome_SpringBoot/           # Source code Backend Java Spring Boot
│   ├── src/main/java/              # Các Controller, Entity, Repository, Service
│   ├── src/main/resources/         # Cấu hình properties, giao diện HTML/CSS/JS
│   └── pom.xml
│
├── SmartHome_ESP32/                # Source code Firmware ESP32
│   └── SmartHome_ESP32.ino         # File C++ cho Arduino IDE
│
└── SmartHome_AIVision/             # Source code AI Nhận Diện Khuôn Mặt
    ├── main.py                     # Chạy webcam, so khớp và gọi API
    ├── requirements.txt            # Danh sách thư viện Python
    └── dataset/                    # Thư mục để bỏ ảnh khuôn mặt gốc vào
```

---

## 2. Hướng Dẫn Cài Đặt (Setup Guide)

### Phần A: Khởi chạy Spring Boot & MQTT (Backend)
1. Bật **MySQL** (XAMPP/WAMP/LAMP). Database `smarthome_db` sẽ được tự động tạo.
2. Đổi mật khẩu CSDL trong file `SmartHome_SpringBoot/src/main/resources/application.properties` (Mặc định: `root`/`10092005Long`).
3. Bật **Mosquitto Broker** (Mặc định ở port `1883`).
4. Chạy class `SmartHomeApplication.java` trên IDE (IntelliJ, VS Code, Eclipse).
5. Mở Web: [http://localhost:8080](http://localhost:8080).

### Phần B: Nạp Code ESP32 (Arduino IDE)
1. **Sơ đồ chân cắm (Pinout Mapping):**
   - D4  -> Chân Data của DHT11 / DHT22 (Nhiệt độ, độ ẩm).
   - D34 -> Chân Analog (A0) của MQ-2 (Cảm biến Gas/Khói).
   - D5  -> Chân Digital (D0) của Cảm biến Lửa (Flame).
   - D18 -> Chân tín hiệu Relay điều khiển Đèn.
   - D19 -> Chân tín hiệu Relay điều khiển Quạt.
   - D21 -> Chân cắm Còi Buzzer báo động.
   - D22 -> Chân PWM điều khiển Servo cửa.

2. **Cấu hình trên `SmartHome_ESP32.ino`:**
   Mở file và thay đổi các hằng số ngay dòng đầu tiên để kết nối mạng:
   ```cpp
   const char* ssid        = "TEN_WIFI_NHA_BAN";
   const char* password    = "MAT_KHAU_WIFI";
   const char* mqtt_server = "192.168.1.xxx"; // Đổi thành IP IPv4 của Laptop chạy Spring Boot
   ```
3. Cài đặt các thư viện trong Arduino IDE (Library Manager): `PubSubClient`, `ArduinoJson`, `DHT sensor library`, `ESP32Servo`.
4. Chọn Board: *ESP32 Dev Module* và bấm Upload. Mở Serial Monitor (Baud: 115200) để xem ESP32 kết nối MQTT thành công chưa.

### Phần C: Nhận Diện Khuôn Mặt AI (Python)
1. Đảm bảo máy đã cài Python (3.9 - 3.11). Mở Terminal vào thư mục `SmartHome_AIVision`.
2. Cài thư viện:
   ```bash
   pip install -r requirements.txt
   ```
   *(Lưu ý: Thư viện `face_recognition` có thể yêu cầu cài đặt CMake/dlib trên Windows).*
3. Tạo thư mục `dataset/` ngang hàng file `main.py` và bỏ 1-2 bức ảnh rõ khuôn mặt của bạn vào đó. Tên file ảnh (VD: `Phung_Van_Long.jpg`) sẽ được lấy làm tên gửi lên Server.
4. **Cấu hình API IP:**
   Mở `main.py`, sửa dòng IP ở mục cấu hình thành IP máy chủ:
   ```python
   SPRING_BOOT_API_URL = "http://192.168.1.xxx:8080/api/ai/face-log"
   ```
5. Chạy AI:
   ```bash
   python main.py
   ```
   Webcam máy tính sẽ hiện lên. Nếu nhận diện trúng tên bạn, AI sẽ tự động gọi REST API xuống Server Spring Boot, Server sẽ lưu log, đồng thời gửi lệnh MQTT làm ESP32 quay Servo góc 90 độ để mở cửa!

---

## 3. Kiến Trúc Luồng Sự Kiện (Data Flow)
* **Giao tiếp ESP32 <-> Spring Boot:** Qua MQTT giao thức Publish/Subscribe cực kỳ nhanh (Topic: `smarthome/sensor` và `smarthome/device/control`).
* **Giao tiếp Web <-> Spring Boot:** Bằng Fetch API truyền tải JSON và AJAX Polling / Web Speech API thu âm giọng nói dịch thành text tiếng Việt.
* **Giao tiếp Python <-> Spring Boot:** HTTP POST thuần túy với JSON Payload.

🎉 Chúc bạn hoàn thành xuất sắc đồ án IoT SmartHome!
