# Phân công backend nhóm 2 người

Phạm vi dưới đây là phân công đã chốt cho giai đoạn code. Khung cấu hình chung đã tạo sẵn.
Mỗi người làm trọn controller, DTO, nghiệp vụ, repository, migration và test của service mình.

## Người 1 — bạn

| Phạm vi | Công việc cần triển khai |
|---|---|
| Cấu hình chung | Maven parent/version, Docker Compose, PostgreSQL init, CI, common-web, README |
| API Gateway | Routing, CORS; tích hợp xác thực cùng Identity; không chứa business logic |
| Identity | Đăng ký/đăng nhập, password hash, JWT/refresh token, role Customer/Staff/Admin, khóa tài khoản, hồ sơ cơ bản, địa chỉ Customer |
| Catalog | Category, Service, package, add-on, yêu cầu riêng của dịch vụ, giá/quote, promotion, API Admin CRUD và API đọc |
| Finance | QR payment + xác nhận giao dịch, invoice, refund, earning ledger/số dư Staff, withdrawal + trạng thái duyệt/chi tiền |

Thứ tự: cấu hình chung → Identity + Catalog → tích hợp Booking với người 2 → Finance.
QR creation không đồng nghĩa thanh toán thành công; chỉ ghi nhận sau khi xác minh với nhà cung cấp/ngân hàng.
Ví Staff là sổ ghi nhận thu nhập/nợ phải trả nội bộ; tạo lệnh rút và xác nhận chuyển tiền là hai bước khác nhau.
Finance phải có idempotency cho webhook, payment, earning và refund để tránh ghi nhận tiền hai lần.

## Người 2

| Phạm vi | Công việc cần triển khai |
|---|---|
| Booking | Booking preview/create, Mode A chọn Staff, Mode B matching, assignment, Staff nhận/từ chối, thực hiện/cập nhật trạng thái, hủy booking, history, review |
| Staff scheduling | Staff availability, vùng hoạt động, năng lực được Admin xác nhận, hạn chế nhận việc, chống trùng lịch/giữ slot |
| Matching | Constraint filtering → score/ranking; CP-SAT khi luồng cơ bản đã ổn |
| AI Assistant | Conversation, intent, slot filling, BookingDraft, hỏi thiếu thông tin, RAG, tool calling, preview → Customer confirm → tạo Booking; voice nếu còn thời gian |

Năng lực Staff đặt ở Booking vì là đầu vào trực tiếp của matching.
Identity chỉ giữ hồ sơ cơ bản; Catalog sở hữu định nghĩa Service; Booking sở hữu quan hệ Staff–Service
và mức năng lực đã duyệt. API Admin phê duyệt năng lực vẫn thuộc Booking và do người 2 viết.

## Điểm phối hợp

| Luồng | Bên sở hữu | Bên gọi |
|---|---|---|
| Xác thực/JWT + thông tin account | Identity / Người 1 | Tất cả service |
| Service requirements, price quote, promotion | Catalog / Người 1 | Booking, AI |
| Booking preview/create/status, matching | Booking / Người 2 | AI, Finance khi cần kiểm tra booking |
| Payment/refund/earning/withdrawal | Finance / Người 1 | Booking, Gateway |
| Hội thoại và draft | AI / Người 2 | Mobile/Web |

Trước khi code tích hợp phải chốt DTO, status enum, lỗi, timeout và idempotency.
Booking lưu snapshot giá/yêu cầu đã xác nhận để thay đổi Catalog không làm đổi lịch sử.
Finance lấy booking amount từ API tin cậy, không tin số tiền do frontend gửi.
Booking/Finance không có transaction database chung: lưu trạng thái trung gian, retry có idempotency
và cơ chế reconciliation cho payment/refund/earning; không gọi HTTP rồi giả định luôn thành công.

## Việc cấu hình đã làm và việc nghiệp vụ chưa làm

Đã làm: 6 app skeleton, parent Maven, common-web, routes/CORS, 5 database/role,
Flyway V1/V2, 53 bảng domain/kỹ thuật, seed demo local, Docker build/Compose,
script chạy/kiểm tra database, tests, CI, hướng dẫn Git và ghi chú đồng bộ Class Diagram.
Chưa làm: JPA business entities/endpoints, JWT/security, thanh toán thật, AI provider,
matching/CP-SAT, message broker và notification. Không coi system/info là API nghiệp vụ hoàn chỉnh.

Người 2 không phải tạo lại project, Dockerfile, database hoặc dependency nền.
Người 2 đọc docs/database/CLASS_DIAGRAM_SYNC.md và schema V2 để viết entity/API;
schema bổ sung dùng V3, không sửa V2 đã áp dụng.
Nếu cần dependency riêng, thêm trong pom.xml của service mình; chỉ phối hợp sửa parent khi cần version chung.
