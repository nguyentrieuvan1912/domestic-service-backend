# Kiểm tra cấu hình backend

## Bổ sung Catalog chỉ đọc — 2026-10-07

API categories/services/detail đã chạy qua Gateway với PostgreSQL thật; Web/Expo Web tích hợp.
Không thay schema, dữ liệu demo, phân công hay nghiệp vụ Booking/AI/Finance/Identity.
Gateway cho phép thêm localhost:5174 (Web CleanMaster); vẫn giữ 5173/8086, từ chối origin ngoài allowlist.
Xem docs/api-contracts/catalog.md về DTO/phạm vi đã triển khai.
Hướng dẫn chạy/test thủ công và báo cáo duyệt riêng người 1 nằm trong .local-notes/person1/ (không commit).

Đã kiểm tra local ngày 2026-10-05:

- Maven reactor build/verify thành công trên Java 21.
- 8 test đạt: 5 service context + HTTP/database health, 2 Gateway routing/CORS,
  1 common-web validation/problem JSON.
- Docker multi-stage build thành công cho cả 6 app.
- PostgreSQL container healthy, cổng host 5433.
- 5 database/role đã tạo và mỗi database áp dụng Flyway V1/V2/V3 + demo repeatable thành công.
- ID bản ghi/reference dùng BIGINT tự tăng, demo có ID số ngắn; giữ dữ liệu và FK khi chuyển UUID demo.
- 53 bảng domain/kỹ thuật, 205 dòng demo sau seed UI Catalog, 47 bảng có mẫu và 6 bảng vận hành để trống.
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
