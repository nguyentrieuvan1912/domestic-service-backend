# Nội dung gửi thành viên 2

Bạn phụ trách **booking-service** và **ai-assistant-service** trong repo
https://github.com/nguyentrieuvan1912/domestic-service-backend.

Người 1 đã chuẩn bị Maven parent, Gateway, Docker Compose, PostgreSQL, CI và skeleton
của cả hai service. Clone sau khi người 1 push phần cấu hình lên main.
Bạn chỉ cần Git + Docker Desktop; thêm JDK 21 nếu chạy service bằng IDE.

## Bắt đầu

```powershell
git clone https://github.com/nguyentrieuvan1912/domestic-service-backend.git
cd domestic-service-backend
Copy-Item .env.example .env
.\scripts\dev.ps1 person2
git switch -c feat/p2-booking-preview
```

Identity vẫn chỉ có endpoint kiểm tra, chưa có API xác thực.
Catalog đã có GET categories/services/services/{id}: đọc docs/api-contracts/catalog.md để dùng DTO số Long,
filter/pagination và packages/addOns/requirements. API quotes/Admin CRUD vẫn chưa triển khai.
Không chờ người 1 xong toàn bộ: chốt contract trước, dùng test stub/client mock cho API chưa triển khai.
Không tạo endpoint giả báo thành công thanh toán/đăng nhập trong code production.

## Phần việc của bạn theo thứ tự

1. Booking: đọc schema V2/V3 và docs/database/CLASS_DIAGRAM_SYNC.md, viết JPA entity/status;
   ID hiện dùng Long/BIGINT tự tăng; thay đổi tiếp theo dùng migration V4; làm preview/create Mode A.
   staff_reservations đã có exclusion constraint chống slot trùng; triển khai hold/expiry ở application.
2. Staff: availability, vùng hoạt động, năng lực do Admin duyệt, nhận/từ chối việc và trạng thái thực hiện.
3. Mode B: lọc ràng buộc, chấm điểm, assignment; tối ưu CP-SAT sau khi flow cơ bản chạy.
4. Hủy booking, lịch sử, review; tích hợp API Finance của người 1 cho payment/refund/earning.
5. AI: conversation → intent/slot filling → BookingDraft → Catalog/Booking preview
   → Customer xác nhận → tạo Booking. AI không tự xác nhận thanh toán hay tự quyết định giá.
6. RAG/tool calling; voice và notification chỉ khi phần lõi ổn.

Mỗi service gồm controller/application/domain/infrastructure/dto/config.
Code và migration nằm trong hai service của bạn. booking_db dùng booking_app;
ai_db dùng ai_app. Không đọc trực tiếp database Identity/Catalog/Finance.

## Phối hợp API

Dùng /api/v1/booking/** và /api/v1/ai-assistant/**.
Cập nhật docs/api-contracts/booking.md và ai-assistant.md cùng PR.
Gửi DTO/error/status cần dùng cho người 1 trước khi triển khai client.
Dùng Long/BIGINT cho ID (JSON số), thời gian ISO-8601 có offset, số tiền VND dưới dạng số nguyên.
Giá và điều kiện dịch vụ phải do Catalog/Booking backend xác nhận.
Khóa/hold slot phải do Booking đảm bảo bằng database, không chỉ kiểm tra ở frontend.
Retry create/payment phải có idempotency; không có transaction chung giữa các service.

## Git

```powershell
git add booking-service docs/api-contracts/booking.md
git commit -m "feat(booking): add booking preview"
git fetch origin
git rebase origin/main
git push -u origin feat/p2-booking-preview
```

Tạo PR vào main; CI phải pass. Mỗi PR một nghiệp vụ nhỏ, không format cả repo.
Trước khi rebase cần commit hoặc stash thay đổi local. Không force-push nhánh chung/main.
Khi sửa root pom.xml, common-web, compose.yml hoặc Gateway, báo người 1 trước.
Trong một service, chỉ owner tạo migration mới để không trùng version. V1/V2/V3 đã áp dụng, không sửa.
Booking/AI đã có dữ liệu mẫu local; 6 bảng vận hành của toàn hệ thống để trống có chủ ý.
Đọc docs/GIT_WORKFLOW.md và docs/api-contracts/README.md trước khi code tích hợp.
