# Hiệu chỉnh bản nhận dạng giọng nói

`POST /api/v1/ai-assistant/voice/refinements` yêu cầu Bearer token của khách hàng.

Body là `{ "text": "bản nhận dạng" }`, tối đa 4000 ký tự và phải có nội dung.
Kết quả gồm `text`, `originalText` và `corrected`. Bản gốc được trả lại để khách đối chiếu.

Mobile gọi một lần sau khi dừng thu và đã nhận được bản chữ hợp lệ. Bản xem trước trong lúc thu dùng API nhận dạng hiện tại. Mô hình Ollama hiện có hiệu chỉnh từ gần âm với tên dịch vụ đọc từ Catalog thật. Backend giới hạn mức thay đổi và bảo vệ số cùng từ phủ định. Khi không hiệu chỉnh được hoặc dịch vụ phụ thuộc lỗi, backend trả bản gốc với `corrected=false`.

Endpoint không lưu hội thoại hay tạo đơn. Khách kiểm tra ô nhập và gửi tin nhắn qua luồng chat hiện có.
