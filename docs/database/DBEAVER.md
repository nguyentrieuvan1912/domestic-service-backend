# Xem PostgreSQL bằng DBeaver

PostgreSQL của backend chạy trong Docker trên máy, không dùng PostgreSQL dự án khác ở cổng 5432.
Thông số lấy từ compose.yml và .env; nếu đã đổi .env thì dùng giá trị hiện tại của bạn.

## Tạo kết nối

1. Mở DBeaver → Database → New Database Connection → PostgreSQL → Next.
2. Host localhost; Port 5433; Database booking_db (hoặc database bên dưới).
3. Username domestic_admin; Password lấy từ POSTGRES_PASSWORD trong .env.
   Nếu giữ nguyên file mẫu, password local là local_admin_change_me.
4. Test Connection; tải JDBC driver nếu DBeaver yêu cầu; Finish.
5. Mở connection → Schemas → public → Tables. Chọn bảng → tab Data để xem dữ liệu.
   Nếu chưa thấy bảng mới, Refresh kết nối hoặc thư mục Tables.

[Tài liệu tạo connection chính thức](https://dbeaver.com/docs/dbeaver/Create-Connection/).

Nên tạo 5 connection riêng, cùng host/port/user/password, khác Database và connection name:

| Connection name | Database |
|---|---|
| Domestic - Identity | identity_db |
| Domestic - Catalog | catalog_db |
| Domestic - Booking | booking_db |
| Domestic - Finance | finance_db |
| Domestic - AI | ai_db |

Bạn có thể connect database postgres để quản trị, nhưng database postgres không chứa bảng nghiệp vụ.
Dùng admin cho kiểm tra local tổng thể. Mỗi app vẫn dùng role riêng, không dùng admin.

## Xem dữ liệu

Trong booking_db mở SQL Editor và chạy:

```sql
SELECT booking_code, booking_mode, status, starts_at, total_amount FROM bookings;
SELECT * FROM booking_assignments;
SELECT * FROM staff_reservations;
```

Trong finance_db:

```sql
SELECT * FROM staff_balances;
SELECT transaction_type, direction, bucket, amount FROM wallet_transactions ORDER BY created_at;
SELECT * FROM withdrawals;
SELECT * FROM payments;
SELECT * FROM refunds;
```

Trong từng database:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename;
```

Database đã tách: trong booking_db không chạy được SELECT từ finance_db.staff_balances.
DBeaver ERD hiển thị FK trong từng database; quan hệ xuyên service được ghi trong CLASS_DIAGRAM_SYNC.md.

## Nếu không kết nối được

Chạy từ thư mục backend:

```powershell
docker compose up -d --wait postgres
docker compose ps
```

Connection refused: kiểm tra Docker Desktop và cổng **5433**.
Password authentication failed: kiểm tra .env, tránh dùng password PostgreSQL khác ở 5432.
Đổi password .env sau khi đã tạo volume không tự đổi role password trong PostgreSQL.
Không xóa volume để chữa lỗi kết nối vì sẽ mất dữ liệu.

Nếu clone về chỉ chạy postgres, sẽ mới có database, chưa có bảng nghiệp vụ.
Chạy tất cả service để Flyway tự tạo bảng và demo:

```powershell
.\scripts\dev.ps1 all
.\scripts\verify-db.ps1
```

Compose local mặc định bật profile dev-seed. Muốn database mới chỉ có schema,
đặt SPRING_PROFILES_ACTIVE=default trong .env trước khi chạy service.
Tắt dev-seed không tự xóa demo đã được thêm; không dùng volume demo cho production.
Trên database đã seed, bỏ profile có thể khiến Flyway báo repeatable migration đã áp dụng
nhưng không tìm thấy trong locations. Giữ dev-seed cho volume local hiện tại; cấu hình default
dành cho database mới. Không sửa/xóa flyway_schema_history để bỏ qua lỗi.
Chạy Java local/IDE muốn seed: thêm SPRING_PROFILES_ACTIVE=dev-seed vào .env hoặc profile trong Run Configuration.
