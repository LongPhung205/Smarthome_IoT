document.addEventListener('DOMContentLoaded', () => {
    // Khởi tạo thư viện Flatpickr cho ô chọn thời gian
    flatpickr("#scheduleTime", {
        enableTime: true,
        dateFormat: "Y-m-d H:i",
        minDate: "today",
        time_24hr: true
    });

    loadSchedules();
});

// Chuyển đổi tên thiết bị
function getDeviceName(deviceKey) {
    switch(deviceKey) {
        case 'light': return 'Đèn Chiếu Sáng';
        case 'fan': return 'Quạt Làm Mát';
        case 'door': return 'Cửa Chính';
        case 'alarm': return 'Còi Báo Động';
        default: return deviceKey;
    }
}

// Lấy danh sách lịch hẹn
async function loadSchedules() {
    const tbody = document.getElementById('scheduleList');
    tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: #64748b; padding: 2rem;"><i class="fa-solid fa-spinner fa-spin"></i> Đang tải dữ liệu...</td></tr>';
    
    try {
        const response = await fetch('/api/schedules');
        if (!response.ok) throw new Error("Lỗi tải danh sách");
        
        const schedules = await response.json();
        
        if (schedules.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: #64748b; padding: 2rem;">Chưa có lịch hẹn nào.</td></tr>';
            return;
        }

        let html = '';
        schedules.forEach(s => {
            const statusClass = s.isActive ? 'tag-success' : 'tag-warning';
            const statusText = s.isActive ? 'Đang Chờ' : 'Tạm Dừng';
            const actionText = s.action === 'ON' ? 'BẬT' : 'TẮT';
            const actionClass = s.action === 'ON' ? 'tag-warning' : 'tag-denied';
            
            html += `
                <tr>
                    <td><strong>${getDeviceName(s.deviceName)}</strong></td>
                    <td><span class="tag ${actionClass}">${actionText}</span></td>
                    <td style="font-weight: bold; font-size: 1.1rem; color: var(--accent-blue);">${s.scheduleTime}</td>
                    <td><span class="tag ${statusClass}" id="status-tag-${s.id}">${statusText}</span></td>
                    <td>
                        <button onclick="toggleSchedule(${s.id})" class="action-btn btn-toggle ${s.isActive ? 'active' : ''}" title="${s.isActive ? 'Tạm dừng' : 'Kích hoạt'}">
                            <i class="fa-solid ${s.isActive ? 'fa-toggle-on' : 'fa-toggle-off'}"></i>
                        </button>
                        <button onclick="deleteSchedule(${s.id})" class="action-btn btn-delete" title="Xóa lịch">
                            <i class="fa-solid fa-trash-can"></i>
                        </button>
                    </td>
                </tr>
            `;
        });
        
        tbody.innerHTML = html;
        
    } catch (err) {
        console.error(err);
        tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: #ef4444; padding: 2rem;">Lỗi tải dữ liệu. Vui lòng thử lại!</td></tr>';
    }
}

// Thêm lịch hẹn mới
async function addSchedule(e) {
    e.preventDefault();
    
    const deviceName = document.getElementById('deviceName').value;
    const action = document.getElementById('action').value;
    const scheduleTime = document.getElementById('scheduleTime').value;
    
    try {
        const res = await fetch('/api/schedules', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                deviceName: deviceName,
                action: action,
                scheduleTime: scheduleTime,
                isActive: true
            })
        });
        
        if (res.ok) {
            Swal.fire({
                title: 'Thành công!',
                text: 'Đã thêm lịch hẹn mới.',
                icon: 'success',
                background: '#1e293b',
                color: '#f8fafc',
                confirmButtonColor: '#38bdf8'
            });
            loadSchedules();
            document.getElementById('scheduleTime').value = '';
        } else {
            throw new Error("Lỗi khi thêm");
        }
    } catch (err) {
        console.error(err);
        Swal.fire('Lỗi', 'Không thể thêm lịch hẹn', 'error');
    }
}

// Chuyển đổi trạng thái Bật/Tắt của lịch hẹn
async function toggleSchedule(id) {
    try {
        const res = await fetch(`/api/schedules/${id}/toggle`, {
            method: 'PUT'
        });
        if (res.ok) {
            loadSchedules();
        }
    } catch (err) {
        console.error("Lỗi khi chuyển đổi trạng thái:", err);
    }
}

// Xóa lịch hẹn
async function deleteSchedule(id) {
    const result = await Swal.fire({
        title: 'Xóa lịch hẹn?',
        text: "Bạn không thể hoàn tác hành động này!",
        icon: 'warning',
        showCancelButton: true,
        background: '#1e293b',
        color: '#f8fafc',
        confirmButtonColor: '#ef4444',
        cancelButtonColor: '#64748b',
        confirmButtonText: 'Xóa ngay',
        cancelButtonText: 'Hủy'
    });

    if (result.isConfirmed) {
        try {
            const res = await fetch(`/api/schedules/${id}`, {
                method: 'DELETE'
            });
            if (res.ok) {
                Swal.fire({
                    title: 'Đã xóa!',
                    text: 'Lịch hẹn đã bị xóa.',
                    icon: 'success',
                    background: '#1e293b',
                    color: '#f8fafc',
                    confirmButtonColor: '#38bdf8'
                });
                loadSchedules();
            }
        } catch (err) {
            console.error("Lỗi khi xóa:", err);
        }
    }
}
