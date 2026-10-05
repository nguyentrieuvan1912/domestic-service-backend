# Domestic Service Backend

Backend monorepo cho nhóm 2 người. Mobile và Web giữ repo riêng.
Nền tảng hiện tại là **khung cấu hình chạy được**, chưa triển khai đăng nhập, CRUD,
booking, thanh toán hay AI. Endpoint system/info chỉ kiểm tra kết nối, không phải API nghiệp vụ.

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
| PostgreSQL | 5433 | 5 database | Cấu hình chung / Người 1 |

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

Postgres init tự tạo 5 database cùng 5 role riêng; role thường không có quyền truy cập database khác.
Admin role chỉ dùng để khởi tạo/quản trị. App không dùng admin credentials.
Flyway chạy migration trong mỗi service khi khởi động; Hibernate chỉ validate và không tự tạo bảng.
V1 là bảng metadata của khung; tạo bảng nghiệp vụ từ V2 trở đi.
Không sửa migration đã chạy, không tạo foreign key/JPA relation sang service khác.

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
  `npx expo start --port 8086`; CORS mặc định cho Web 5173 và Expo Web 8086.
- clients.*.base-url đã có sẵn trong mỗi service để viết HTTP client sau.
- Chưa có xác thực/phân quyền JWT. Người 1 triển khai Identity và security trước khi cung cấp API thật.
- Chưa cấu hình nhà cung cấp QR, API key AI, Redis, message broker hoặc CP-SAT.
  Đây là các dependency nghiệp vụ, bổ sung khi thống nhất API và nhà cung cấp.

## Chất lượng và phối hợp

`mvnw verify` chạy kiểm tra context/database health bằng H2 và kiểm tra Gateway routing/CORS.
`scripts/smoke.ps1` kiểm tra routing qua các app thật; Docker dùng PostgreSQL + Flyway thật.
GitHub Actions chạy cả Maven verify và Docker integration trên PR/main.
Branch protection vẫn cần bật trong GitHub Settings; file CI không tự bật bảo vệ main.

- [Phân công nhóm](docs/TEAM_ASSIGNMENT.md)
- [Nội dung gửi người 2](docs/PERSON_2_HANDOFF.md)
- [Quy trình Git](docs/GIT_WORKFLOW.md)
- [Quy ước API](docs/api-contracts/README.md)

Chỉ có một Maven Wrapper ở root là chuẩn dùng chung. Wrapper cũ trong api-gateway
được giữ lại để bảo toàn file sẵn có; không dùng wrapper đó cho build cả repo.
