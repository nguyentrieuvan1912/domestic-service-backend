# Quy ước hợp đồng API

Các endpoint nghiệp vụ trong file từng service là **dự thảo cần hai người chốt**.
Hiện chỉ system/info và actuator/health đã có code.
Mỗi owner cập nhật contract của mình trong cùng PR triển khai API.

- Prefix /api/v1/{service}/...; Gateway không strip prefix.
- ID dùng Long/BIGINT tự tăng trong mỗi bảng, JSON là số (ví dụ id: 1, customerId: 2).
  ID tham chiếu service khác giữ đúng ID do service sở hữu cấp; không shared entity/JPA relation.
  Mobile mock đang dùng string: có thể chuẩn hóa String(id) ở adapter API hoặc cập nhật type khi tích hợp.
- JSON camelCase. Date/time ISO-8601 có offset; lưu UTC; diễn giải lịch theo Asia/Ho_Chi_Minh.
- VND: integer/Long, không float/double. Finance tính toán/ledger với kiểm tra overflow.
- Thành công trả resource DTO; danh sách trả {items, page, size, total}.
- Lỗi dùng application/problem+json: type, title, status, detail, instance;
  validation thêm errors: {field: message}.
- POST tạo booking/payment/refund/earning nhận Idempotency-Key; lưu và kiểm tra ở service sở hữu.
- Contract chốt rõ status enum, phiên bản quote, timeout, retry và cơ chế bù trừ/reconciliation.
- Internal API sẽ xác thực service-to-service khi triển khai security; không mặc định public qua Gateway.
- Chưa cấu hình JWT. Trước tích hợp thật chốt issuer/audience/key rotation/role và kiểm tra JWT ở từng service,
  không chỉ tại Gateway. Admin endpoint cần phân quyền ngay tại owning service.
- Tài liệu OpenAPI/Swagger bổ sung khi có DTO/endpoint thật, tránh sinh tài liệu giả cho scaffold.

Mỗi contract cần ghi method/path, request/response mẫu, validation, role, lỗi,
idempotency, status transition và consumer. Thay đổi phá tương thích phải phối hợp owner bên gọi.
