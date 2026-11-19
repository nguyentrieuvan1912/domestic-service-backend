# Hiệu chỉnh bản nhận dạng giọng nói

`POST /api/v1/ai-assistant/voice/refinements` yêu cầu Bearer token của khách hàng.

Body là `{ "text": "bản nhận dạng" }`, tối đa 4000 ký tự và phải có nội dung.
Kết quả gồm `text`, `originalText` và `corrected`. Bản gốc được trả lại để khách đối chiếu.

Mobile gửi bản thu đến API nhận dạng một lần sau khi dừng thu, rồi gọi API hiệu đính nếu nhận được bản chữ hợp lệ. Ô nhập hiện kết quả khi xử lý xong. Mô hình Ollama hiệu chỉnh từ gần âm với tên dịch vụ đọc từ Catalog thật. Backend giới hạn mức thay đổi và bảo vệ số cùng từ phủ định. Khi không hiệu chỉnh được hoặc dịch vụ phụ thuộc lỗi, backend trả bản gốc với `corrected=false`.

Endpoint không lưu hội thoại hay tạo đơn. Khách kiểm tra ô nhập và gửi tin nhắn qua luồng chat hiện có.
