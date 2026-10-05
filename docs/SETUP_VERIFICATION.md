# Kiểm tra cấu hình backend

Đã kiểm tra local ngày 2026-10-05:

- Maven reactor build/verify thành công trên Java 21.
- 8 test đạt: 5 service context + HTTP/database health, 2 Gateway routing/CORS,
  1 common-web validation/problem JSON.
- Docker multi-stage build thành công cho cả 6 app.
- PostgreSQL container healthy, cổng host 5433.
- 5 database/role đã tạo và mỗi database áp dụng Flyway V1 thành công.
- 5 endpoint system/info gọi được qua Gateway 8080.
- booking_app có CONNECT vào booking_db và không có CONNECT vào catalog_db.
- Docker Compose config hợp lệ; .env được Git ignore.

Đây là kiểm tra khung cấu hình, không xác nhận nghiệp vụ login/booking/payment/AI.
GitHub Actions đã được cấu hình nhưng chỉ chạy trên GitHub sau khi push/PR.
Branch protection và review policy cần bật trong GitHub Settings.

Chạy lại sau khi clone:

```powershell
.\scripts\dev.ps1 check
.\scripts\dev.ps1 all
.\scripts\smoke.ps1
```
