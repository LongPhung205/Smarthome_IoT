/**
 * voice_recognition.js - Điều khiển nhà thông minh bằng giọng nói (Web Speech API)
 */
document.addEventListener('DOMContentLoaded', () => {
    const micBtn = document.getElementById('micBtn');
    const voiceStatus = document.getElementById('voiceStatus');
    const voiceTranscript = document.getElementById('voiceTranscript');

    if (!micBtn) return;

    // Kiểm tra hỗ trợ Web Speech API
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

    if (!SpeechRecognition) {
        voiceStatus.innerText = "Trình duyệt không hỗ trợ Web Speech API. Vui lòng dùng Chrome hoặc Edge.";
        micBtn.disabled = true;
        micBtn.style.opacity = "0.5";
        return;
    }

    const recognition = new SpeechRecognition();
    recognition.lang = 'vi-VN'; // Nhận diện tiếng Việt
    recognition.continuous = false;
    recognition.interimResults = false;

    let isListening = false;

    micBtn.addEventListener('click', () => {
        if (isListening) {
            recognition.stop();
        } else {
            try {
                recognition.start();
            } catch (err) {
                console.error("Lỗi khởi động mic:", err);
            }
        }
    });

    recognition.onstart = () => {
        isListening = true;
        micBtn.classList.add('listening');
        voiceStatus.innerText = "Đang lắng nghe bạn nói...";
        voiceTranscript.innerText = "Nói ví dụ: 'Bật đèn', 'Mở quạt', 'Mở cửa'...";
    };

    recognition.onresult = async (event) => {
        const transcript = event.results[0][0].transcript;
        voiceTranscript.innerText = `"${transcript}"`;
        voiceStatus.innerText = "Đang xử lý câu lệnh...";

        // Gửi lệnh lên Spring Boot ApiController
        try {
            const response = await fetch('/api/voice', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    command: transcript,
                    user: 'Gia đình'
                })
            });

            const data = await response.json();
            voiceStatus.innerText = data.reply || "Đã gửi lệnh thành công!";

            // Phản hồi giọng nói qua loa (Text-To-Speech)
            speakResponse(data.reply);

            // Cập nhật lại trạng thái các nút gạt trên giao diện
            if (data.states && window.updateDeviceUI) {
                window.updateDeviceUI(data.states);
            }
        } catch (error) {
            console.error("Lỗi khi gửi lệnh giọng nói:", error);
            voiceStatus.innerText = "Lỗi kết nối tới Server!";
        }
    };

    recognition.onerror = (event) => {
        console.warn("Lỗi nhận diện giọng nói:", event.error);
        voiceStatus.innerText = "Không nhận diện được. Hãy thử lại!";
        resetMic();
    };

    recognition.onend = () => {
        resetMic();
    };

    function resetMic() {
        isListening = false;
        micBtn.classList.remove('listening');
        if (voiceStatus.innerText === "Đang lắng nghe bạn nói...") {
            voiceStatus.innerText = "Nhấn vào micro để bắt đầu nói";
        }
    }

    function speakResponse(text) {
        if ('speechSynthesis' in window && text) {
            const utterance = new SpeechSynthesisUtterance(text);
            utterance.lang = 'vi-VN';
            utterance.rate = 1.0;
            window.speechSynthesis.speak(utterance);
        }
    }
});
