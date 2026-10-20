# Quy ước hợp đồng API

Các file từng service có thể chứa **dự thảo cần hai người chốt**.
Phạm vi nghiệp vụ đã triển khai cập nhật 14/10/2026 nằm trong [operations-v5.md](operations-v5.md), gồm Customer/Staff mobile và Admin web. Đối chiếu file này trước khi dùng các endpoint dự kiến trong contract cũ.
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
- Các endpoint hiện có dùng requestKey/clientKey theo [operations-v5.md](operations-v5.md); lưu và kiểm tra ở service sở hữu. Idempotency-Key trong contract cũ là đề xuất, không mặc định đã được hỗ trợ.
- Contract chốt rõ status enum, phiên bản quote, timeout, retry và cơ chế bù trừ/reconciliation.
- Internal events đã dùng X-Service-Key riêng. Bearer session được xác minh qua Identity và role được kiểm tra tại từng owning service. JWT issuer/audience/key rotation là phần nâng cấp chưa triển khai.
- Tài liệu OpenAPI/Swagger bổ sung khi có DTO/endpoint thật, tránh sinh tài liệu giả cho scaffold.

Mỗi contract cần ghi method/path, request/response mẫu, validation, role, lỗi,
idempotency, status transition và consumer. Thay đổi phá tương thích phải phối hợp owner bên gọi.

## Customer frontend integration

See [customer-data.md](customer-data.md) for owned addresses, notification read state, public support intake and promotion display. The full customer rollout plan is in the workspace-level CUSTOMER_API_PLAN.md.
