# ai-assistant API contract — draft

Owner: Người 2. Consumer: Mobile/Web.
Dự thảo: POST /api/v1/ai-assistant/conversations;
POST /api/v1/ai-assistant/conversations/{id}/messages;
POST /api/v1/ai-assistant/drafts/{id}/confirm.
Chốt draft schema, thiếu slot, preview, explicit Customer confirmation,
conversation ownership, tool authorization, timeout và idempotency khi tạo Booking.
AI gọi Catalog/Booking API, không query database service khác hay tự đặt giá.
API key/provider/RAG/voice được thêm trong giai đoạn nghiệp vụ.

Đã triển khai: GET /api/v1/ai-assistant/system/info trả {service, stage: "scaffold"}.
