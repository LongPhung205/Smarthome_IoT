/**
 * realtime_dashboard.js - Cập nhật dữ liệu cảm biến thời gian thực và điều khiển thiết bị
 */

let sensorChart = null;
let lastFaceLogId = null; // Lưu ID của log khuôn mặt gần nhất
let isScanning = false;   // Trạng thái đang quét Camera
let scanTimeoutId = null; // Timer để bắt timeout 10 giây

document.addEventListener('DOMContentLoaded', () => {
    setupDeviceSwitches();
    setupCameraScanBtn();
    initChart(); // Khởi tạo biểu đồ
    
    fetchLatestSensor();
    fetchLatestAILog();
    fetchSensorHistory(); // Lấy dữ liệu lịch sử lần đầu

    // Polling lấy dữ liệu cảm biến mới nhất mỗi 2.5 giây
    setInterval(() => {
        fetchLatestSensor();
        fetchSensorHistory(); // Cập nhật biểu đồ theo thời gian thực
    }, 2500);

    // Polling dữ liệu AI camera mỗi 3 giây
    setInterval(fetchLatestAILog, 3000);
});

/**
 * Khởi tạo biểu đồ đường (Chart.js) cho nhiệt độ và khí gas
 */
function initChart() {
    const ctx = document.getElementById('sensorChart');
    if (!ctx) return;

    sensorChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'Nhiệt độ (°C)',
                    borderColor: '#38bdf8',
                    backgroundColor: 'rgba(56, 189, 248, 0.1)',
                    borderWidth: 2,
                    tension: 0.3,
                    fill: true,
                    data: []
                },
                {
                    label: 'Khí Gas MQ2 (PPM)',
                    borderColor: '#f59e0b',
                    backgroundColor: 'rgba(245, 158, 11, 0.1)',
                    borderWidth: 2,
                    tension: 0.3,
                    fill: true,
                    data: []
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                x: {
                    grid: { color: 'rgba(15, 23, 42, 0.06)' },
                    ticks: { color: '#64748b' }
                },
                y: {
                    grid: { color: 'rgba(15, 23, 42, 0.06)' },
                    ticks: { color: '#64748b' }
                }
            },
            plugins: {
                legend: {
                    labels: { color: '#0f172a' }
                }
            }
        }
    });
}

/**
 * Gọi API lấy dữ liệu cảm biến mới nhất và cập nhật các Widget
 */
async function fetchLatestSensor() {
    try {
        const res = await fetch('/api/sensor/latest');
        if (res.status === 200) {
            const data = await res.json();
            if (data) {
                const tempEl = document.getElementById('valTemp');
                const humEl = document.getElementById('valHum');
                const gasEl = document.getElementById('valGas');
                const flameEl = document.getElementById('valFlame');

                if (tempEl && data.temperature !== null) tempEl.innerText = data.temperature.toFixed(1);
                if (humEl && data.humidity !== null) humEl.innerText = data.humidity.toFixed(1);

                // Cập nhật trạng thái Cảm biến lửa / hồng ngoại
                if (flameEl) {
                    if (data.flameDetected === true) {
                        flameEl.innerText = 'Có lửa';
                        flameEl.style.color = '#f43f5e';
                    } else {
                        flameEl.innerText = 'Không có';
                        flameEl.style.color = 'var(--text-main)';
                    }
                }

                // Cảnh báo khẩn cấp khi phát hiện lửa
                if (data.flameDetected === true) {
                    if (!window.isFlameAlarmActive) {
                        window.isFlameAlarmActive = true;
                        if (typeof Swal !== 'undefined') {
                            Swal.fire({
                                title: 'CẢNH BÁO CHÁY!',
                                text: 'Cảm biến hồng ngoại phát hiện có lửa! Đang kích hoạt các biện pháp an toàn.',
                                icon: 'error',
                                background: '#ffffff',
                                color: '#0f172a',
                                confirmButtonColor: '#f43f5e',
                                confirmButtonText: 'Đã Rõ'
                            }).then(() => {
                                window.isFlameAlarmActive = false;
                            });
                        }
                    }
                } else {
                    window.isFlameAlarmActive = false;
                }

                if (gasEl && data.gasLevel !== null) {
                    gasEl.innerText = data.gasLevel.toFixed(0);
                    // Cảnh báo khí ga cao
                    const heroCard = document.getElementById('heroGasCard');
                    if (data.gasLevel > 400) {
                        gasEl.style.color = '#f43f5e';
                        if (heroCard) heroCard.classList.add('gas-alert');
                        
                        const badge = document.getElementById('gasStatusBadge');
                        if (badge) {
                            badge.className = 'tag tag-denied';
                            badge.innerText = 'PHÁT HIỆN RÒ RỈ GAS!';
                        }

                        // Kích hoạt Popup nếu chưa hiển thị
                        if (!window.isGasAlarmActive) {
                            window.isGasAlarmActive = true;
                            if (typeof Swal !== 'undefined') {
                                Swal.fire({
                                    title: 'CẢNH BÁO NGUY HIỂM!',
                                    text: 'Phát hiện rò rỉ khí Gas! Hệ thống đang tự động Bật Quạt, Mở Cửa và Hú Còi để xử lý.',
                                    icon: 'error',
                                    background: '#ffffff',
                                    color: '#0f172a',
                                    confirmButtonColor: '#f43f5e',
                                    confirmButtonText: 'Đã Rõ'
                                }).then(() => {
                                    // Reset khi user bấm nút tắt popup để lần sau còn báo tiếp nếu vẫn cháy
                                    window.isGasAlarmActive = false; 
                                });
                            }
                        }

                        // Tự động gạt công tắc trên giao diện (Đồng bộ với MqttService Auto-Mitigation)
                        if (typeof window.updateDeviceUI === 'function') {
                            window.updateDeviceUI({
                                fan: true,
                                door: true,
                                alarm: true
                            });
                        }

                    } else {
                        gasEl.style.color = 'var(--text-main)';
                        if (heroCard) heroCard.classList.remove('gas-alert');
                        const badge = document.getElementById('gasStatusBadge');
                        if (badge) {
                            badge.className = 'tag tag-success';
                            badge.innerText = 'Mức Gas An Toàn';
                        }
                        window.isGasAlarmActive = false; // Reset trạng thái
                    }
                }
            }
        }
    } catch (err) {
        console.warn("Lỗi khi fetch sensor data:", err);
    }
}

/**
 * Gọi API lấy danh sách dữ liệu lịch sử để vẽ lại biểu đồ
 */
async function fetchSensorHistory() {
    if (!sensorChart) return;
    try {
        const res = await fetch('/api/sensor/history?limit=15');
        if (res.ok) {
            const list = await res.json();
            if (Array.isArray(list) && list.length > 0) {
                // Đảo ngược mảng để thời gian tăng dần từ trái sang phải
                const sorted = [...list].reverse();

                const labels = sorted.map(item => {
                    const d = new Date(item.recordedAt);
                    return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`;
                });

                const temps = sorted.map(item => item.temperature);
                const gases = sorted.map(item => item.gasLevel);

                sensorChart.data.labels = labels;
                sensorChart.data.datasets[0].data = temps;
                sensorChart.data.datasets[1].data = gases;
                sensorChart.update();
            }
        }
    } catch (err) {
        console.warn("Lỗi khi load chart data:", err);
    }
}

/**
 * Đăng ký sự kiện click cho các công tắc bật/tắt thiết bị
 */
function setupDeviceSwitches() {
    const switches = document.querySelectorAll('.device-switch-input');

    switches.forEach(sw => {
        sw.addEventListener('change', async (e) => {
            const device = e.target.getAttribute('data-device');
            const state = e.target.checked;
            const item = e.target.closest('.device-item');

            // Nếu là MỞ CỬA, yêu cầu mã PIN
            if (device === 'door' && state === true) {
                // Trả công tắc về tắt tạm thời
                e.target.checked = false;
                
                const { value: pin } = await Swal.fire({
                    title: 'Bảo Mật 2 Lớp',
                    text: 'Vui lòng nhập mã PIN bảo mật để mở cửa:',
                    input: 'password',
                    inputPlaceholder: 'Nhập mã PIN 4 số...',
                    showCancelButton: true,
                    confirmButtonText: 'Mở Cửa',
                    cancelButtonText: 'Hủy',
                    inputValidator: (value) => {
                        if (!value) {
                            return 'Bạn chưa nhập mã PIN!';
                        }
                    }
                });

                if (pin) {
                    try {
                        const response = await fetch('/api/device/control-secure', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                device: device,
                                state: state,
                                pin: pin,
                                source: 'Web Dashboard Switch'
                            })
                        });
                        
                        const data = await response.json();
                        if (data.success) {
                            Swal.fire('Thành công!', 'Cửa đã được mở.', 'success');
                            e.target.checked = true;
                            if (item) {
                                item.classList.add('active');
                                const statusSpan = item.querySelector('.device-status');
                                if (statusSpan) statusSpan.innerText = 'Đang MỞ';
                            }
                        } else {
                            Swal.fire('Thất bại!', data.message || 'Mã PIN sai.', 'error');
                        }
                    } catch (error) {
                        Swal.fire('Lỗi!', 'Lỗi kết nối tới Server.', 'error');
                    }
                }
                return; // Ngừng thực thi luồng bên dưới
            }

            // Luồng bình thường cho Quạt, Đèn, hoặc Đóng cửa
            try {
                const response = await fetch('/api/device/control', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        device: device,
                        state: state,
                        source: 'Web Dashboard Switch'
                    })
                });

                const data = await response.json();
                if (data.success) {
                    if (item) {
                        item.classList.toggle('active', state);
                        const statusSpan = item.querySelector('.device-status');
                        if (statusSpan) {
                            if (device === 'door') {
                                statusSpan.innerText = state ? 'Đang MỞ' : 'Đang ĐÓNG';
                            } else {
                                statusSpan.innerText = state ? 'Đang BẬT' : 'Đang TẮT';
                            }
                        }
                    }
                } else {
                    e.target.checked = !state;
                }
            } catch (error) {
                console.error("Lỗi khi điều khiển thiết bị:", error);
                e.target.checked = !state;
            }
        });
    });
}

/**
 * Hàm toàn cục hỗ trợ cập nhật UI thiết bị từ lệnh giọng nói
 */
window.updateDeviceUI = function (states) {
    if (!states) return;

    for (const [device, state] of Object.entries(states)) {
        const sw = document.querySelector(`.device-switch-input[data-device="${device}"]`);
        if (sw) {
            sw.checked = state;
            const item = sw.closest('.device-item');
            if (item) {
                item.classList.toggle('active', state);
                const statusSpan = item.querySelector('.device-status');
                if (statusSpan) {
                    if (device === 'door') {
                        statusSpan.innerText = state ? 'Đang MỞ' : 'Đang ĐÓNG';
                    } else {
                        statusSpan.innerText = state ? 'Đang BẬT' : 'Đang TẮT';
                    }
                }
            }
        }
    }
};

/**
 * Gọi API lấy log mới nhất để cập nhật bảng điều khiển AI Camera
 */
async function fetchLatestAILog() {
    try {
        const res = await fetch('/api/logs');
        if (res.ok) {
            const logs = await res.json();
            // Lọc ra sự kiện nhận diện khuôn mặt gần nhất
            const faceLog = logs.find(log => log.eventType === 'FACE_RECOGNITION');
            
            if (faceLog) {
                // Nếu đang trong quá trình quét, chỉ chấp nhận log mới (có ID lớn hơn)
                if (isScanning) {
                    if (lastFaceLogId !== null && faceLog.id <= lastFaceLogId) {
                        return; // Bỏ qua vì đây là log cũ
                    }
                    
                    // Phát hiện được log mới! Tắt trạng thái scanning
                    isScanning = false;
                    clearTimeout(scanTimeoutId);
                    
                    // Khôi phục nút bấm
                    const btn = document.getElementById('btnScanFace');
                    if (btn) {
                        btn.disabled = false;
                        btn.style.opacity = '1';
                        btn.innerHTML = '<i class="fa-solid fa-expand"></i> Kích Hoạt Quét Camera';
                    }
                }
                
                // Cập nhật lastFaceLogId
                lastFaceLogId = faceLog.id;
                
                const statusEl = document.getElementById('aiFaceStatus');
                const nameEl = document.getElementById('aiFaceName');
                
                if (statusEl && nameEl) {
                    if (faceLog.status === 'SUCCESS') {
                        statusEl.innerText = '✅ Nhận diện thành công chủ nhà:';
                        statusEl.style.color = '#10b981'; // Xanh lá
                        nameEl.innerText = faceLog.personName || 'Unknown';
                        
                        // Tự động cập nhật nút gạt (slider) Cửa sang MỞ
                        if (typeof window.updateDeviceUI === 'function') {
                            window.updateDeviceUI({ door: true });
                        }
                    } else {
                        statusEl.innerText = '❌ Phát hiện người lạ:';
                        statusEl.style.color = '#ef4444'; // Đỏ
                        nameEl.innerText = faceLog.personName || 'Unknown';
                    }
                }
            }
        }
    } catch (err) {
        console.warn("Lỗi khi load AI logs:", err);
    }
}

/**
 * Xử lý sự kiện nút Bấm Quét Khuôn Mặt
 */
function setupCameraScanBtn() {
    const btn = document.getElementById('btnScanFace');
    if (!btn) return;

    btn.addEventListener('click', async () => {
        if (isScanning) return;
        
        const statusEl = document.getElementById('aiFaceStatus');
        const nameEl = document.getElementById('aiFaceName');
        
        // Cập nhật UI sang trạng thái chờ
        isScanning = true;
        btn.disabled = true;
        btn.style.opacity = '0.5';
        btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang chờ kết quả...';
        
        if (statusEl) {
            statusEl.innerText = '⏳ Đang quét khuôn mặt...';
            statusEl.style.color = '#f59e0b';
        }
        if (nameEl) {
            nameEl.innerText = '--';
        }

        try {
            // Gửi lệnh bật thiết bị ảo "camera_scan"
            const response = await fetch('/api/device/control', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    device: 'camera_scan',
                    state: true,
                    source: 'Web Dashboard Button'
                })
            });

            if (!response.ok) {
                throw new Error("Lỗi API gọi Camera");
            }

            // Thiết lập Timeout 10 giây nếu Python AI bị lỗi / tắt
            scanTimeoutId = setTimeout(() => {
                if (isScanning) {
                    isScanning = false;
                    btn.disabled = false;
                    btn.style.opacity = '1';
                    btn.innerHTML = '<i class="fa-solid fa-expand"></i> Kích Hoạt Quét Camera';
                    
                    if (statusEl) {
                        statusEl.innerText = '❌ Lỗi: Camera không phản hồi!';
                        statusEl.style.color = '#ef4444';
                    }
                }
            }, 10000);

        } catch (error) {
            console.error("Lỗi khi gọi Camera:", error);
            isScanning = false;
            btn.disabled = false;
            btn.style.opacity = '1';
            btn.innerHTML = '<i class="fa-solid fa-expand"></i> Kích Hoạt Quét Camera';
            
            if (statusEl) {
                statusEl.innerText = '❌ Lỗi kết nối tới Server';
                statusEl.style.color = '#ef4444';
            }
        }
    });
}

