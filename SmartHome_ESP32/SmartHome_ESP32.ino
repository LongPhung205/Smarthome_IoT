#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include <LiquidCrystal_I2C.h> 

// ==========================================
// CẤU HÌNH BIẾN MẠNG & SERVER
// ==========================================
const char* ssid        = "tienfg";        // Thay bằng tên Wifi
const char* password    = "11111111";    // Thay bằng mật khẩu Wifi
const char* mqtt_server = "broker.emqx.io";         // Sử dụng Public Broker EMQX
const int   mqtt_port   = 1883;

// ==========================================
// QUY HOẠCH CHÂN CẮM MỚI (Tránh xung đột I2C)
// ==========================================
// Chân 21 và 22 được mặc định dùng cho SDA và SCL của LCD 1602 I2C
#define MQ2_PIN       34    // Chân A0/D34 cắm ngõ ra Analog của MQ-2
#define IR_PIN        5     // Chân D5 cắm ngõ ra Digital (D0) của Cảm biến hồng ngoại
#define FLAME_PIN     IR_PIN // Đồng bộ tên FLAME_PIN với IR_PIN (Cảm biến lửa / hồng ngoại)
#define RELAY_FAN_PIN 19    // Chân D19 cắm Relay để bật/tắt Quạt DC
#define SERVO1_PIN    18    // Chân D18 cắm Servo 1 (Ví dụ: Cửa chính)
#define SERVO2_PIN    14    // Chân D14 cắm Servo 2 (Ví dụ: Cửa sổ / Quạt gió)
#define LIGHT_PIN     15    // Chân D15 cắm Đèn chiếu sáng (Active HIGH)

// ==========================================
// CÁC ĐỐI TƯỢNG TOÀN CỤC
// ==========================================
WiFiClient espClient;
PubSubClient client(espClient);
Servo servo1;
Servo servo2;
LiquidCrystal_I2C lcd(0x27, 16, 2); // Địa chỉ I2C thường là 0x27 (hoặc 0x3F)

unsigned long lastSendTime = 0;
const long interval = 5000; 

// Biến lưu trạng thái hiển thị
bool fanState = false;
bool lightState = false;

// ==========================================
// SETUP
// ==========================================
void setup() {
  Serial.begin(115200);
  Serial.println("\n--- DANG KHOI DONG HE THONG ---");
  // Khởi tạo LCD
  lcd.init();
  lcd.backlight();
  lcd.setCursor(0, 0);
  lcd.print("SmartHome ESP32");
  lcd.setCursor(0, 1);
  lcd.print("Dang khoi dong..");

  // Cài đặt chân cắm cảm biến & Relay
  pinMode(IR_PIN, INPUT);   // Chân D5 đọc tín hiệu cảm biến hồng ngoại
  pinMode(MQ2_PIN, INPUT);
  pinMode(RELAY_FAN_PIN, OUTPUT);
  pinMode(LIGHT_PIN, OUTPUT);

  // Mặc định tắt thiết bị
  digitalWrite(RELAY_FAN_PIN, HIGH); // Relay kích mức thấp (Active LOW) nên xuất HIGH để TẮT
  digitalWrite(LIGHT_PIN, LOW);      // Đèn chân dương (Active HIGH) nên xuất LOW để TẮT

  // Cấu hình Timer cho ESP32Servo (Giúp tạo xung PWM chuẩn xác hơn để Servo không bị liệt)
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  ESP32PWM::allocateTimer(2);
  ESP32PWM::allocateTimer(3);

  // Khởi tạo 2 Servo
  servo1.setPeriodHertz(50); // Tần số 50Hz chuẩn cho Servo SG90
  servo1.attach(SERVO1_PIN, 500, 2400); 
  servo1.write(0); // Góc 0 mặc định
  
  servo2.setPeriodHertz(50); // Tần số 50Hz chuẩn cho Servo SG90
  servo2.attach(SERVO2_PIN, 500, 2400); 
  servo2.write(0); // Góc 0 mặc định

  // Kết nối mạng
  setup_wifi();
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);
}

void setup_wifi() {
  delay(10);
  WiFi.begin(ssid, password);
  Serial.println();
  Serial.print("Dang ket noi toi Wi-Fi: ");
  Serial.println(ssid);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  
  // HIỂN THỊ ĐÃ KẾT NỐI WIFI LÊN SERIAL
  Serial.println();
  Serial.println("=> Da ket noi WiFi thanh cong!");
  Serial.print("=> Dia chi IP: ");
  Serial.println(WiFi.localIP());
  
  lcd.clear();
  lcd.print("WiFi Connected!");
}

// ==========================================
// NHẬN LỆNH TỪ SPRING BOOT
// ==========================================
void callback(char* topic, byte* payload, unsigned int length) {
  String message = "";
  for (int i = 0; i < length; i++) {
    message += (char)payload[i];
  }
  
  JsonDocument doc;
  DeserializationError error = deserializeJson(doc, message);

  if (error) return;

  String device = doc["device"].as<String>();
  String action = doc["action"].as<String>(); // "ON" hoặc "OFF"
  bool state = (action == "ON"); 

  if (device == "fan") {
    fanState = state;
    // Relay kích mức thấp (Active LOW): BẬT = LOW, TẮT = HIGH
    digitalWrite(RELAY_FAN_PIN, state ? LOW : HIGH);
    Serial.println(state ? "QUẠT: BẬT" : "QUẠT: TẮT");
  } 
  else if (device == "door") {
    // Điều khiển CẢ HAI Servo cùng lúc
    servo1.write(state ? 90 : 0);
    servo2.write(state ? 90 : 0);
    Serial.println(state ? "2 SERVO: ĐANG MỞ" : "2 SERVO: ĐANG ĐÓNG");
  }
  else if (device == "light") {
    lightState = state;
    digitalWrite(LIGHT_PIN, state ? HIGH : LOW); // Đèn chân dương: BẬT = HIGH, TẮT = LOW
    Serial.println(state ? "ĐÈN: BẬT" : "ĐÈN: TẮT");
  }
}

void reconnect() {
  while (!client.connected()) {
    // HIỂN THỊ ĐANG KẾT NỐI MQTT LÊN SERIAL
    Serial.print("Dang ket noi toi MQTT Broker... ");
    String clientId = "ESP32-SmartHome-" + String(random(0xffff), HEX);
    if (client.connect(clientId.c_str())) {
      Serial.println("=> Thanh cong!");
      client.subscribe("smarthome/device/control");
    } else {
      Serial.print("=> That bai, ma loi = ");
      Serial.print(client.state());
      Serial.println(" (Thu lai sau 5 giay)");
      delay(5000);
    }
  }
}

// ==========================================
// VÒNG LẶP CHÍNH
// ==========================================
void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();

  long now = millis();
  if (now - lastSendTime > interval) {
    lastSendTime = now;
    
    // Đọc cảm biến
    int gasVal = analogRead(MQ2_PIN);
    int irVal = digitalRead(IR_PIN); 
    bool hasFlame = (irVal == LOW); // Tín hiệu mức LOW khi phát hiện hồng ngoại/lửa (Active LOW)

    // Hiển thị lên LCD
    lcd.clear();
    
    // Dòng 1: Hiển thị Nhiệt độ & Gas
    lcd.setCursor(0, 0);
    lcd.print("Gas:"); lcd.print(gasVal);
    lcd.print("     "); // Xóa khoảng trống thừa

    // Dòng 2: Hiển thị Trạng thái Lửa hoặc Thiết bị
    lcd.setCursor(0, 1);
    if (hasFlame) {
      lcd.print("! CANH BAO LUA !");
    } else {
      lcd.print("An toan | Q:");
      lcd.print(fanState ? "ON" : "OFF");
    }

    // In log Serial để tiện giám sát qua Arduino Serial Monitor
    Serial.print("Gas: "); Serial.print(gasVal);
    Serial.print(" | Hong ngoai (D5): "); Serial.println(hasFlame ? "PHAT HIEN" : "AN TOAN");

    // Gửi dữ liệu MQTT lên Spring Boot
    JsonDocument doc;
    doc["temperature"] = 0;
    doc["gasLevel"] = gasVal;
    doc["flame"] = hasFlame;
    doc["ir"] = hasFlame;

    char jsonBuffer[256];
    serializeJson(doc, jsonBuffer);

    client.publish("smarthome/sensor", jsonBuffer);
  }
}
