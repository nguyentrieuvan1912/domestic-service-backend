# Notification database — thiết kế và phạm vi triển khai

Ngày chuẩn bị schema: 15/10/2026. PostgreSQL `notification_db` thuộc `notification-service`,
có role riêng `notification_app`. ID dùng BIGINT tự tăng, thời điểm dùng TIMESTAMPTZ,
payload dùng JSONB. Schema được quản lý bằng Flyway V1/V2, không có dữ liệu seed.

## Các bảng

| Bảng | Mục đích | Quan hệ nội bộ |
|---|---|---|
| notification_events | Sự kiện đầu vào từ Booking/Finance hoặc service khác; source_service + event_key chống trùng; lưu payload, trạng thái, số lần xử lý và lỗi | 1 sự kiện → 0..* notifications |
| notifications | Thông báo cho một người dùng: loại, tiêu đề, nội dung, tham chiếu nghiệp vụ, payload, created_at, read_at | 0..1 sự kiện → mỗi thông báo; 1 thông báo → 0..* deliveries |
| notification_devices | Thiết bị/điểm nhận push thuộc người dùng; lưu device_key, platform, provider, push_token, trạng thái | 1 thiết bị → 0..* deliveries |
| notification_deliveries | Trạng thái gửi qua IN_APP hoặc PUSH: lần thử, mã provider, lỗi, lịch thử lại, thời điểm gửi/nhận | Thuộc đúng 1 notification; PUSH thuộc 1 device, IN_APP không có device |
| service_schema_metadata | Tên owning service và thời điểm khởi tạo schema | Bảng hạ tầng độc lập |
| flyway_schema_history | Lịch sử migration, checksum và kết quả thực thi | Bảng hạ tầng do Flyway quản lý |

`user_id` là định danh do Identity cấp, không tạo bảng users bản sao và không có FK xuyên database.
FK ghép `(notification_id,user_id)` và `(device_id,user_id)` ở deliveries bảo đảm thông báo
và thiết bị cùng người nhận. Mỗi sự kiện tạo tối đa một thông báo cho mỗi user. Một thông báo
có thể gửi tới nhiều thiết bị; mỗi cặp notification/channel/device chỉ có một delivery.

Không lưu cả is_read và read_at: isRead được suy ra từ read_at. Trạng thái SENT/DELIVERED
bắt buộc có timestamp phù hợp; bản ghi PENDING không chứng minh đã gửi thành công.
Push token là dữ liệu riêng tư, không đưa vào ảnh minh họa dữ liệu, log hoặc báo cáo.

## Trạng thái hiện tại và chuyển đổi

Đã chuẩn bị database, role, schema, service nền chạy Flyway và health; chưa có API nghiệp vụ,
consumer/worker, FCM/Expo adapter, xác thực người nhận hoặc lịch nhắc việc trong service mới.
Không có route Gateway cho service mới ở giai đoạn schema-only; system/info gọi trực tiếp 8087.

API `/api/v1/identity/notifications` và producer Booking/Finance vẫn dùng `identity_db.notifications`.
Không di chuyển/xóa dữ liệu hoặc thay đổi Mobile/Web trong bước chuẩn bị schema này. Hai database
chưa được đồng bộ, không coi notifications mới là nguồn dữ liệu đang vận hành.

Khi triển khai tách service: chốt contract xác thực/event, chuyển dữ liệu thật giữ người nhận,
trạng thái đọc và khóa chống trùng; cập nhật Gateway/producer/client; kiểm tra đối soát rồi
mới ngừng bảng cũ bằng migration tiếp theo. Tránh duy trì hai nguồn ghi độc lập.

## Chạy trên volume hiện có

Từ root backend, cấu hình NOTIFICATION_DB_USER/NOTIFICATION_DB_PASSWORD trong `.env` ignored:

```powershell
.\scripts\prepare-notification-db.ps1
docker compose up -d --no-deps --build notification-service
Invoke-RestMethod http://localhost:8087/actuator/health
.\scripts\verify-db.ps1
```

Init của volume mới tự tạo database thứ sáu. Volume cũ cần prepare script; không xóa volume
hay chạy lại init của các database đang dùng. Script không reset role/password đã có.

## Xuất ERD bằng DBeaver

Tạo connection PostgreSQL với host localhost, port 5433, database notification_db;
credentials dùng role riêng trong `.env` hoặc tài khoản admin local hiện có.
Refresh → Schemas → public → Tables. Chọn bốn bảng nghiệp vụ → View Diagram → Arrange Diagram
→ Save diagram as… → PNG. Bỏ hai bảng hạ tầng nếu hình chỉ trình bày nghiệp vụ.

## Đoạn mô tả đưa vào báo cáo

Cơ sở dữ liệu **notification_db** được thiết kế để quản lý thông báo, sự kiện đầu vào và
trạng thái gửi thông báo của hệ thống. Bảng **notification_events** lưu sự kiện từ các service
và khóa chống xử lý trùng. Bảng **notifications** lưu nội dung thông báo cho từng người nhận
cùng thời điểm tạo và đọc. Bảng **notification_devices** quản lý thiết bị nhận thông báo đẩy,
trong khi **notification_deliveries** ghi nhận kênh gửi, trạng thái, số lần thử và kết quả gửi.
Các khóa ngoại liên kết sự kiện, thông báo và thiết bị, đồng thời bảo đảm delivery thuộc
đúng người nhận. Hai bảng **flyway_schema_history** và **service_schema_metadata** phục vụ
quản lý cấu trúc database. Tham chiếu người dùng được lưu bằng định danh do Identity quản lý,
không tạo khóa ngoại xuyên database. Schema đã được chuẩn bị trước; tích hợp gửi thông báo
và chuyển luồng hiện tại từ Identity sang Notification Service thuộc giai đoạn triển khai tiếp theo.
