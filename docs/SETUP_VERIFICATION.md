# Kiểm tra cấu hình backend

Đã kiểm tra local ngày 2026-10-05:

- Maven reactor build/verify thành công trên Java 21.
- 8 test đạt: 5 service context + HTTP/database health, 2 Gateway routing/CORS,
  1 common-web validation/problem JSON.
- Docker multi-stage build thành công cho cả 6 app.
- PostgreSQL container healthy, cổng host 5433.
- 5 database/role đã tạo và mỗi database áp dụng Flyway V1/V2 + demo repeatable thành công.
- 53 bảng domain/kỹ thuật, 61 dòng demo, 47 bảng có mẫu và 6 bảng vận hành để trống.
- scripts/verify-db.ps1 đạt: default address, staff slot overlap/adjacent ranges,
  rating 1..5, refund limit, immutable ledger và cùng-staff ownership của withdrawal.
- Demo wallet ledger = cached available balance = 105000 VND.
- 5 endpoint system/info gọi được qua Gateway 8080.
- booking_app có CONNECT vào booking_db và không có CONNECT vào catalog_db.
- Docker Compose config hợp lệ; .env được Git ignore.

Đây là kiểm tra cấu hình/schema và fixture, không xác nhận API nghiệp vụ login/booking/payment/AI.
GitHub Actions đã được cấu hình nhưng chỉ chạy trên GitHub sau khi push/PR.
Branch protection và review policy cần bật trong GitHub Settings.

Chạy lại sau khi clone:

```powershell
.\scripts\dev.ps1 check
.\scripts\dev.ps1 all
.\scripts\smoke.ps1
```
