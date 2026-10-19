# Domestic Service Backend

Backend monorepo cho nhóm 2 người. Mobile và Web giữ repo riêng.
Nền tảng hiện tại có cấu hình chạy được, 70 bảng domain/kỹ thuật và dữ liệu demo local đã có từ trước. Identity V11 bổ sung danh mục 168 phường/xã/đặc khu TP.HCM chính thức, dùng chung cho địa chỉ Customer và khu vực Staff. Notification có 4 bảng chuẩn bị trước, chưa chuyển chức năng thông báo đang chạy trong Identity.
Đã có API đọc Catalog (danh mục, tìm/lọc/phân trang, chi tiết dịch vụ) và tích hợp Web/Mobile.
Đã nối Web với Identity: đăng ký/đăng nhập khách hàng và admin, quản lý trạng thái khách hàng, xét duyệt ứng viên và cấp tài khoản Staff.
Đã nối luồng Customer/Staff mobile và Admin web: quote, booking, lịch/nhận ca, tiến độ, chat, đánh giá, thông báo, CRUD Catalog/Promo, kiểm duyệt và Finance ghi sổ/quyết toán tiền mặt. Xem [phạm vi thực tế theo V5](docs/api-contracts/operations-v5.md).
QR/webhook/chuyển khoản/hoàn tiền qua nhà cung cấp, LiveKit và pipeline AI đặt dịch vụ chưa hoàn thiện; không giả lập thành công. AI đã có hội thoại sở hữu và adapter riêng, cần provider thực tế để trả lời.
Endpoint system/info chỉ kiểm tra kết nối, không phải API nghiệp vụ.

Java 21 · Spring Boot 4.1.1 · Spring Cloud 2025.1.3 · Maven Wrapper · PostgreSQL 17 · Docker Compose.
Cặp phiên bản Boot/Cloud theo [compatibility matrix của Spring](https://spring.io/projects/spring-cloud/).

## Bắt đầu sau khi clone

Cần Git và Docker Desktop chạy Linux containers. Nếu chạy Java trên máy: cài JDK 21.
Không cần cài Maven hoặc PostgreSQL riêng. Chạy mọi lệnh từ thư mục backend.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
.\scripts\dev.ps1 all
.\scripts\smoke.ps1
```

macOS/Linux:

```bash
cp .env.example .env
docker compose --profile backend up -d --build
curl http://localhost:8080/api/v1/catalog/system/info
```

Lần đầu tải JDK image, Maven dependencies và PostgreSQL có thể mất vài phút.
Docker build tự biên dịch Java bằng Maven Wrapper; không cần build trên máy trước.

| Service | Cổng trên máy | Database | Chủ sở hữu |
|---|---:|---|---|
| api-gateway | 8080 | Không có | Người 1 |
| identity-service | 8081 | identity_db | Người 1 |
| catalog-service | 8082 | catalog_db | Người 1 |
| booking-service | 8083 | booking_db | Người 2 |
| finance-service | 8084 | finance_db | Người 1 |
| ai-assistant-service | 8085 | ai_db | Người 2 |
| notification-service (schema/health) | 8087 | notification_db | Người 1 |
| PostgreSQL | 5433 | 6 database | Cấu hình chung / Người 1 |

Trong Docker, từng app dùng cổng 8080 và PostgreSQL dùng 5432.
Trên máy dùng 5433 để tránh PostgreSQL của dự án khác. Chỉ bind các cổng Docker vào localhost.
Mobile/Web gọi Gateway, không gọi service trực tiếp. Emulator hoặc điện thoại thật cần cấu hình
địa chỉ host phù hợp; cấu hình hiện tại dành cho phát triển local trên máy.

## Chọn cách chạy

```powershell
.\scripts\dev.ps1 infra     # Chỉ PostgreSQL; chạy Java bằng IDE để debug
.\scripts\dev.ps1 person1   # Gateway + Identity + Catalog + Finance
.\scripts\dev.ps1 person2   # Gateway + Identity + Catalog + Booking + AI
.\scripts\dev.ps1 all       # Toàn bộ backend trong Docker
.\scripts\dev.ps1 check     # Maven verify trên máy, cần JDK 21
.\scripts\dev.ps1 stop      # Dừng container, giữ dữ liệu
```

Các lệnh person1/person2 chỉ thêm/start nhóm tương ứng; không tự dừng service đã chạy trước đó.
Nếu muốn chuyển nhóm và giảm RAM, chạy stop trước. Gateway vẫn có route đến service chưa chạy,
vì vậy chỉ gọi API của các service trong nhóm hiện tại.

Phát triển bằng IDE: chạy infra, import root pom.xml, chọn Main Application của service.
Build/install dependency trước khi chạy một module riêng:

```powershell
.\mvnw.cmd -B --no-transfer-progress install -DskipTests
.\mvnw.cmd -pl booking-service spring-boot:run
```

IDE/Java cần working directory là root backend hoặc thư mục service để đọc .env.
Nếu đổi POSTGRES_PORT cũng đổi DB_PORT trong .env khi chạy Java local.
Nếu container service đang chạy, dừng container đó trước khi chạy IDE cùng cổng:
`docker compose stop booking-service`.

## Database và migration

Postgres init tự tạo 6 database cùng 6 role riêng; role thường không có quyền truy cập database khác.
Volume đã có từ trước cần chạy `.\scripts\prepare-notification-db.ps1` trước khi start Notification.
Xem [thiết kế notification_db và đoạn mô tả báo cáo](docs/database/NOTIFICATION_DB.md).
Admin role chỉ dùng để khởi tạo/quản trị. App không dùng admin credentials.
Flyway chạy migration trong mỗi service khi khởi động; Hibernate chỉ validate và không tự tạo bảng.
V1 là metadata; V2 tạo bảng domain/kỹ thuật; V3 đổi ID sang Long/BIGINT tự tăng.
Các bản ghi demo dùng ID 1, 2, 3... theo bảng. Identity V4–V7 bổ sung tài khoản/ứng viên, Gmail, CommunityPost và hỗ trợ; V8–V10 bổ sung chống trùng notification, kiểm duyệt/bình luận/like và yêu thích. Catalog V4 quản lý quote; Booking V4–V5 quản lý lịch/sự cố/tiến độ/snapshot Staff; Finance V4 quản lý chính sách và khóa chống ghi sổ lặp; AI V4 chống trùng tin nhắn.
Không sửa migration đã chạy, không tạo foreign key/JPA relation sang service khác.

Compose local mặc định bật profile dev-seed và chạy repeatable R__demo_data.sql.
Seed là dữ liệu giả, tài khoản INACTIVE/password disabled, không thể đăng nhập hay chuyển tiền.
Các bảng token/idempotency/outbox để trống. Xem docs/database/INVENTORY.md để biết số dòng từng bảng.
Nếu dùng database mới không cần demo, đặt SPRING_PROFILES_ACTIVE=default trong .env.
Chạy IDE với .env cũ muốn demo: thêm SPRING_PROFILES_ACTIVE=dev-seed.
Xem docs/database/DBEAVER.md trước khi chuyển profile trên volume đã seed.

Init script chỉ chạy khi volume PostgreSQL trống. Đổi password trong .env sau khi đã tạo volume
không đổi password trong database; cần cập nhật role bằng SQL hoặc dùng volume mới có chủ ý.
`docker compose down` giữ volume. Không xóa volume chứa dữ liệu đang cần dùng.

## API và cấu hình

- Routes: /api/v1/identity/**, /api/v1/catalog/**, /api/v1/booking/**,
  /api/v1/finance/**, /api/v1/ai-assistant/**. Gateway giữ nguyên path.
- Health: http://localhost:8081/actuator/health (thay cổng cho service khác).
- Error validation: HTTP 400, application/problem+json với title/detail/errors.
- CORS ở Gateway, cấu hình CORS_ALLOWED_ORIGINS trong .env.
- Expo mặc định dùng 8081, trùng Identity. Khi chạy Mobile cùng backend, dùng
  `npx expo start --port 8090`; CORS cho Web 5173/5174 và Expo Web 8090.
  Web CleanMaster dùng 5174 để không trùng dự án khác. `.env` backend cũ cần thêm
  `http://localhost:5174` vào CORS_ALLOWED_ORIGINS rồi `docker compose up -d api-gateway`.
- Frontend gọi Gateway 8080: Web dùng VITE_API_BASE_URL, Mobile dùng EXPO_PUBLIC_API_BASE_URL.
  Xem [hợp đồng Catalog](docs/api-contracts/catalog.md) về phạm vi đã nối và giới hạn local/network.
- clients.*.base-url được HTTP client dùng để xác minh phiên và gọi owning service, không truy vấn chéo database.
- Identity đã có bearer session có hạn, BCrypt, thu hồi phiên và quyền theo role tại owning service. Internal events cần INTERNAL_SERVICE_TOKEN riêng trong .env. Chưa chuyển sang JWT; xem [hợp đồng Identity](docs/api-contracts/identity.md).
- Chưa cấu hình nhà cung cấp QR, API key AI, Redis, message broker hoặc CP-SAT.
  Đây là các dependency nghiệp vụ, bổ sung khi thống nhất API và nhà cung cấp.

## Chất lượng và phối hợp

`mvnw verify` chạy kiểm tra context/database health bằng H2 và kiểm tra Gateway routing/CORS.
`scripts/smoke.ps1` kiểm tra routing qua các app thật; Docker dùng PostgreSQL + Flyway thật.
`scripts/verify-db.ps1` kiểm tra 70 bảng, migration/seed, slot overlap, refund total,
immutable ledger, wallet ownership, rating và default address; dữ liệu thử được rollback.
GitHub Actions chạy cả Maven verify và Docker integration trên PR/main.
Branch protection vẫn cần bật trong GitHub Settings; file CI không tự bật bảo vệ main.

- [Phân công nhóm](docs/TEAM_ASSIGNMENT.md)
- [Nội dung gửi người 2](docs/PERSON_2_HANDOFF.md)
- [Quy trình Git](docs/GIT_WORKFLOW.md)
- [Quy ước API](docs/api-contracts/README.md)
- [Xem database bằng DBeaver](docs/database/DBEAVER.md)
- [Các điểm cần sửa trên Class Diagram](docs/database/CLASS_DIAGRAM_SYNC.md)
- [Danh sách bảng và dữ liệu demo](docs/database/INVENTORY.md)

Chỉ có một Maven Wrapper ở root là chuẩn dùng chung. Wrapper cũ trong api-gateway
được giữ lại để bảo toàn file sẵn có; không dùng wrapper đó cho build cả repo.

## Đăng nhập và quản trị trên Web

- Frontend: `/register`, `/login`, `/account`, `/admin/login`, `/admin/customers`, `/admin/candidates`, `/admin/staff`.
- Tạo admin đầu tiên: đặt `IDENTITY_BOOTSTRAP_EMAIL`, `IDENTITY_BOOTSTRAP_PHONE`, `IDENTITY_BOOTSTRAP_PASSWORD` trong `.env` ignored,
  chạy `docker compose up -d --build identity-service`; sau đó bỏ ba biến và recreate Identity. Seed demo không có password dùng được.
- Cấp Staff tại bước cuối trang Ứng viên sau tiếp nhận, hồ sơ đủ và đào tạo. Trang Nhân viên lấy tài khoản thật từ Identity.
- Booking/Finance/KPI đã lấy API thật; Finance yêu cầu xác nhận thực nhận tiền mặt và chính sách phí trước quyết toán. Identity không truy vấn database khác.


## Email xác thực đăng ký khách hàng

Web/Mobile đăng ký theo ba bước: Gmail → mã xác thực 6 số → hồ sơ/mật khẩu.
Đặt SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD và IDENTITY_MAIL_FROM trong `.env` ignored theo `.env.example`, rồi rebuild Identity.
Gmail SMTP sử dụng App Password của tài khoản gửi đã bật xác minh hai bước: https://support.google.com/accounts/answer/185833?hl=vi . Không gửi mật khẩu trong chat hay lưu vào frontend.
Chưa cấu hình SMTP thì API gửi mã báo 503; hệ thống không tạo tài khoản trước khi xác thực và không dùng mã mẫu.
Xem docs/api-contracts/identity.md để biết body API mới; body đăng ký cũ chỉ có email không còn hợp lệ.

## Kết nối khách hàng — đợt đầu

Đã bổ sung API sổ địa chỉ theo tài khoản, thông báo và trạng thái đã đọc, tiếp nhận form Liên hệ vào Identity; API hiển thị ưu đãi từ Catalog. Mobile/Web dùng dữ liệu API ở những màn đã nối và chỉ báo thành công sau khi lưu. Xem [contract customer-data](docs/api-contracts/customer-data.md) và CUSTOMER_API_PLAN.md ở workspace để phân biệt phần hoàn thành và phần còn chờ Booking/Finance/AI.

## Cấu trúc code và ánh xạ JPA

Các service dùng tầng `controller`, `dto`, `service`, `entity`, `repository`, `config`.
Package `application` cũ được chuyển thành `service`; các API và luồng nghiệp vụ giữ nguyên.
70 Entity ánh xạ schema của sáu database, bao gồm 44 lớp trong bộ sơ đồ lớp.
Quan hệ nội bộ dùng JPA; tham chiếu sang service khác giữ ID. Các truy vấn đặc thù PostgreSQL
được giữ trong Repository nghiệp vụ/native để bảo toàn khóa đồng thời và thứ tự ghi sổ.

- [Hướng dẫn tìm code, kiến trúc và kiểm thử](docs/BACKEND_CODE_STRUCTURE.md)
- [Bảng đối chiếu lớp – Entity – bảng – Repository](docs/CLASS_CODE_MAPPING.md)
