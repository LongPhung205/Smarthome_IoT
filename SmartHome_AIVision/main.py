import cv2
import face_recognition
import numpy as np
import requests
import os
import re
import json
import time
import sys
import threading
from PIL import Image
import paho.mqtt.client as mqtt
from flask import Flask, Response
from flask_cors import CORS

# Cố định bảng mã UTF-8 cho Terminal Windows
sys.stdout.reconfigure(encoding='utf-8')

# ==========================================
# CẤU HÌNH BIẾN MẠNG & CAMERA
# ==========================================
SPRING_BOOT_API_URL = "http://localhost:8080/api/ai/face-log"
MQTT_BROKER = "broker.emqx.io"
MQTT_PORT = 1883
MQTT_TOPIC_CONTROL = "smarthome/device/control"
CAMERA_INDEX = 0
CONFIDENCE_THRESHOLD = 60.0 # Độ tin cậy trên 50% để nhận diện chủ nhà

app = Flask(__name__)
CORS(app)

# ==========================================
# KHỞI TẠO DỮ LIỆU KHUÔN MẶT ĐÃ BIẾT
# ==========================================
known_face_encodings = []
known_face_names = []

def load_known_faces():
    global known_face_encodings, known_face_names
    known_face_encodings.clear()
    known_face_names.clear()
    
    script_dir = os.path.dirname(os.path.abspath(__file__))
    dataset_dir = os.path.join(script_dir, "dataset")
    if not os.path.exists(dataset_dir):
        os.makedirs(dataset_dir)
        print(f"[*] Đã tạo thư mục '{dataset_dir}'.")
        return

    print("[*] Đang nạp lại kho dữ liệu khuôn mặt...")
    for filename in os.listdir(dataset_dir):
        if filename.lower().endswith(('.png', '.jpg', '.jpeg')):
            # Tự động chuẩn hóa tên (loại bỏ hậu tố _1, _2... nếu có nhiều ảnh cho 1 người)
            raw_name = os.path.splitext(filename)[0]
            clean_name = re.sub(r'(_\d+|\d+)$', '', raw_name).replace("_", " ").title()
            filepath = os.path.join(dataset_dir, filename)
            try:
                pil_image = Image.open(filepath).convert("RGB")
                image_rgb = np.array(pil_image, dtype=np.uint8)
                image_rgb = np.ascontiguousarray(image_rgb)
                
                encodings = face_recognition.face_encodings(image_rgb)
                if len(encodings) > 0:
                    known_face_encodings.append(encodings[0])
                    known_face_names.append(clean_name)
                    print(f"  + Đã nạp thành công: {clean_name} ({filename})")
                else:
                    print(f"  - Cảnh báo: Không tìm thấy khuôn mặt trong ảnh {filename}")
            except Exception as e:
                print(f"  - Lỗi khi đọc ảnh {filename}: {e}")

def send_to_spring_boot(name, confidence, image_url=None):
    print(f"[API] Gửi log cho: {name} ({confidence}%)")
    try:
        payload = {
            "personName": name,
            "confidence": confidence,
            "autoOpenDoor": True
        }
        if image_url:
            payload["imageUrl"] = image_url

        response = requests.post(SPRING_BOOT_API_URL, json=payload, timeout=5)
        if response.status_code == 200:
            print(f"[API] Server phản hồi: {response.json().get('message')}")
    except Exception as e:
        print(f"[API ERROR] Không thể gọi API: {e}")

# ==========================================
# CỜ TRẠNG THÁI AI
# ==========================================
trigger_scan = False
scan_frames_left = 0
person_found = False

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("[*] Đã kết nối MQTT Broker!")
        client.subscribe(MQTT_TOPIC_CONTROL)
    else:
        print(f"[!] Lỗi MQTT: {rc}")

def on_message(client, userdata, msg):
    global trigger_scan, scan_frames_left, person_found
    payload = msg.payload.decode("utf-8")
    try:
        data = json.loads(payload)
        if data.get("device") == "camera_scan" and data.get("action") == "ON":
            print(f"\n[>>>] BẮT ĐẦU QUÉT TÌM KHUÔN MẶT!")
            trigger_scan = True
            scan_frames_left = 180  # Quét trong 180 khung hình (Khoảng 8-9 giây để bắt mặt nhạy hơn)
            person_found = False
            
        elif data.get("device") == "camera_scan" and data.get("action") == "RELOAD_DATASET":
            print(f"\n[>>>] NHẬN ĐƯỢC LỆNH NẠP LẠI DỮ LIỆU TỪ WEB!")
            load_known_faces()
    except Exception as e:
        pass

# Khởi tạo MQTT trong một luồng riêng
client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message

def start_mqtt():
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_forever()

# ==========================================
# STREAMING VIDEO BẰNG FLASK
# ==========================================
video_capture = None

def generate_frames():
    global trigger_scan, scan_frames_left, person_found, video_capture
    
    # Tạo 1 khung hình đen để báo trạng thái chờ
    offline_frame = np.zeros((480, 640, 3), dtype=np.uint8)
    cv2.putText(offline_frame, "CAMERA IS SLEEPING...", (130, 240), cv2.FONT_HERSHEY_SIMPLEX, 1, (100, 100, 100), 2, cv2.LINE_AA)
    cv2.putText(offline_frame, "Bam 'Kich Hoat' de bat", (180, 280), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (150, 150, 150), 1, cv2.LINE_AA)
    ret, offline_buffer = cv2.imencode('.jpg', offline_frame)
    offline_bytes = offline_buffer.tobytes()

    while True:
        if not trigger_scan:
            # Nếu đang ở chế độ chờ, giải phóng camera và phát khung hình đen (tiết kiệm 100% CPU và tắt đèn Camera)
            if video_capture is not None:
                video_capture.release()
                video_capture = None
                print("[-] Đã tắt phần cứng Camera.")
            
            yield (b'--frame\r\n'
                   b'Content-Type: image/jpeg\r\n\r\n' + offline_bytes + b'\r\n')
            time.sleep(1) # Phát 1 FPS để giữ kết nối
            continue

        # Nếu có lệnh quét mà camera chưa bật
        if video_capture is None:
            print("[+] Đang khởi động phần cứng Camera...")
            video_capture = cv2.VideoCapture(CAMERA_INDEX)
            # Đọc bỏ 5 khung hình ban đầu để camera ổn định phơi sáng & cân bằng trắng
            for _ in range(5):
                video_capture.read()
            time.sleep(0.3)

        success, frame = video_capture.read()
        if not success:
            trigger_scan = False
            continue
        
        # Nếu còn lượt quét và chưa tìm thấy người
        if scan_frames_left > 0 and not person_found:
            scan_frames_left -= 1
            
            # Giảm kích thước còn 0.5 để tăng tốc độ xử lý FPS
            small_frame = cv2.resize(frame, (0, 0), fx=0.5, fy=0.5)
            rgb_small_frame = cv2.cvtColor(small_frame, cv2.COLOR_BGR2RGB)
            
            # number_of_times_to_upsample=1 giúp phát hiện khuôn mặt ở xa / góc nghiêng nhạy hơn
            face_locations = face_recognition.face_locations(rgb_small_frame, number_of_times_to_upsample=1)
            face_encodings = face_recognition.face_encodings(rgb_small_frame, face_locations)

            for (top, right, bottom, left), face_encoding in zip(face_locations, face_encodings):
                # Tolerance 0.60 theo chuẩn dlib ResNet (nhạy, chính xác và không bị từ chối oan)
                matches = face_recognition.compare_faces(known_face_encodings, face_encoding, tolerance=0.60)
                name = "Unknown"
                confidence = 0.0

                if len(known_face_encodings) > 0:
                    face_distances = face_recognition.face_distance(known_face_encodings, face_encoding)
                    best_match_index = np.argmin(face_distances)
                    dist = face_distances[best_match_index]

                    # Quy đổi khoảng cách Euclid dlib thành độ tin cậy % trực quan:
                    # - dist <= 0.60 (khớp với người quen): dao động từ 60.0% đến 99.9%
                    # - dist > 0.60 (người lạ / không khớp): dưới 50.0%
                    if dist <= 0.60:
                        calculated_conf = round(100.0 - (dist / 0.60) * 40.0, 1)
                    else:
                        calculated_conf = round(max(0.0, (1.0 - (dist - 0.60) / 0.40) * 50.0), 1)

                    # Chỉ chấp nhận là chủ nhà khi đạt độ tin cậy từ 50% trở lên
                    if matches[best_match_index] and calculated_conf >= CONFIDENCE_THRESHOLD:
                        name = known_face_names[best_match_index]
                        confidence = calculated_conf
                    else:
                        name = "Unknown"
                        confidence = calculated_conf

                # Vẽ khung nhận diện
                top *= 2
                right *= 2
                bottom *= 2
                left *= 2

                is_recognized = (name != "Unknown" and confidence >= CONFIDENCE_THRESHOLD)
                color = (0, 255, 0) if is_recognized else (0, 0, 255)
                cv2.rectangle(frame, (left, top), (right, bottom), color, 2)
                
                label = f"{name} {confidence}%" if is_recognized else (f"Unknown ({confidence}%)" if confidence > 0 else "Unknown")
                cv2.rectangle(frame, (left, bottom - 35), (right, bottom), color, cv2.FILLED)
                font = cv2.FONT_HERSHEY_DUPLEX
                cv2.putText(frame, label, (left + 6, bottom - 6), font, 0.7, (255, 255, 255), 1)

                if is_recognized and not person_found:
                    print(f"[*] BINGO! Đã nhận diện chủ nhà: {name} ({confidence}% >= {CONFIDENCE_THRESHOLD}%)")
                    send_to_spring_boot(name, confidence)
                    person_found = True
                    trigger_scan = False # Dừng quét sau khi tìm thấy
                    break # Chỉ lấy 1 khuôn mặt đầu tiên
            
            # Hết lượt quét mà không thấy ai quen
            if scan_frames_left == 0 and not person_found:
                print("[-] Đã quét xong. Không tìm thấy người quen nào.")
                
                # Chụp ảnh người lạ
                timestamp = int(time.time())
                img_filename = f"intruder_{timestamp}.jpg"
                # Đường dẫn tuyệt đối tới thư mục static của Spring Boot
                img_dir = "D:/IoTBTL/SmartHome_SpringBoot/src/main/resources/static/images/intruders"
                if not os.path.exists(img_dir):
                    os.makedirs(img_dir)
                img_path = os.path.join(img_dir, img_filename)
                cv2.imwrite(img_path, frame)
                print(f"[+] Đã lưu ảnh người lạ: {img_path}")
                
                send_to_spring_boot("Unknown", 0.0, f"/images/intruders/{img_filename}")
                trigger_scan = False

        # Vẽ chữ "ĐANG QUÉT..." lên màn hình nếu đang trong quá trình
        if trigger_scan:
            cv2.putText(frame, "DANG QUET...", (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 165, 255), 2, cv2.LINE_AA)

        ret, buffer = cv2.imencode('.jpg', frame)
        frame = buffer.tobytes()
        yield (b'--frame\r\n'
               b'Content-Type: image/jpeg\r\n\r\n' + frame + b'\r\n')

@app.route('/video_feed')
def video_feed():
    return Response(generate_frames(), mimetype='multipart/x-mixed-replace; boundary=frame')

if __name__ == "__main__":
    print("\n==================================================")
    print("      AI CAMERA DAEMON (STREAMING SERVER)         ")
    print("==================================================\n")
    
    load_known_faces()
    
    # Chạy MQTT ở background
    threading.Thread(target=start_mqtt, daemon=True).start()
    
    # Chạy Flask Server cho Stream
    print("[*] Đang khởi động luồng Video tại http://localhost:5000/video_feed ...")
    app.run(host='0.0.0.0', port=5000, debug=False, use_reloader=False)
