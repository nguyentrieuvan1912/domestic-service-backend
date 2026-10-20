# Database inventory

Nguồn schema: Diagram/SoDoUC/Class Diagram1.jpg (13/10/2026), các góc nhìn ClassDiagramSimple,
model Mobile và nghiệp vụ QR/ví/AI đã chốt. Identity V6 bổ sung CommunityPost; V7 bổ sung support_requests để lưu form Liên hệ. Hai bảng này không thêm seed.

Cập nhật 15/10/2026: có 69 bảng domain/kỹ thuật và baseline 205 dòng demo sau seed UI Catalog; chưa tính service_schema_metadata và flyway_schema_history
(2 bảng hạ tầng trong mỗi database). 47 bảng có demo; 22 bảng không có demo. administrative_units có 168 bản ghi tham chiếu hành chính chính thức, không tính vào 205 dòng demo. Các migration nghiệp vụ không tạo tài khoản hoặc tiền mẫu mới.
Số dòng trên chỉ tính seed, không gồm account/ứng viên được tạo bằng API hoặc bootstrap admin. Không có dữ liệu người dùng/ngân hàng thật trong seed. Tài khoản demo không đăng nhập được.

| Database | Bảng domain/kỹ thuật | Dòng demo |
|---|---:|---:|
| identity_db | 16 | 8 |
| catalog_db | 8 | 152 |
| booking_db | 17 | 17 |
| finance_db | 17 | 21 |
| ai_db | 7 | 7 |
| notification_db | 4 | 0 |

Notification là schema chuẩn bị trước, không seed và chưa thay thế `identity_db.notifications`.
Xem [NOTIFICATION_DB.md](NOTIFICATION_DB.md) về bảng, quan hệ và phạm vi triển khai.

## notification_db

| Bảng | Dòng demo |
|---|---:|
| notification_events | 0 |
| notifications | 0 |
| notification_devices | 0 |
| notification_deliveries | 0 |

## identity_db

| Bảng | Dòng demo |
|---|---:|
| users | 3 |
| admins | 1 |
| customers | 1 |
| staffs | 1 |
| addresses | 1 |
| notifications | 1 |
| refresh_tokens | 0 |
| account_sessions | 0 |
| staff_candidates | 0 |
| registration_verifications | 0 |
| community_posts | 0 |
| support_requests | 0 |
| community_comments | 0 |
| community_likes | 0 |
| customer_favorites | 0 |
| administrative_units | 0 (168 bản ghi tham chiếu chính thức) |

## catalog_db

| Bảng | Dòng demo |
|---|---:|
| service_categories | 16 |
| services | 20 |
| service_packages | 39 |
| add_ons | 73 |
| service_requirements | 1 |
| promotions | 1 |
| promotion_services | 1 |
| price_quotes | 1 |

R__ui_catalog_data.sql bổ sung dữ liệu từ UI mẫu: 16 dịch vụ Mobile, 3 dịch vụ Web;
service/package/add-on demo ID 1 vẫn giữ nguyên cho tham chiếu người 2. Seed local dev-seed
insert-only, không thay schema và không ghi đè nội dung đã sửa. Add-on Web dùng giá mẫu tương
ứng sofa/rèm/nệm/thảm của Mobile; cần xác nhận lại chính sách trước khi triển khai quote.
Số dòng trên là baseline khi chưa thêm dữ liệu nghiệp vụ; không phải constraint số lượng.

## booking_db

| Bảng | Dòng demo |
|---|---:|
| bookings | 3 |
| booking_add_ons | 1 |
| booking_requirement_answers | 1 |
| booking_assignments | 2 |
| booking_status_histories | 2 |
| reviews | 1 |
| staff_areas | 1 |
| staff_availabilities | 1 |
| staff_service_capabilities | 1 |
| staff_restrictions | 1 |
| staff_schedule_settings | 0 |
| booking_incidents | 0 |
| conversations | 1 |
| messages | 1 |
| staff_reservations | 1 |
| idempotency_records | 0 |
| outbox_events | 0 |

## finance_db

| Bảng | Dòng demo |
|---|---:|
| orders | 2 |
| order_details | 2 |
| payments | 2 |
| refunds | 1 |
| invoices | 1 |
| invoice_items | 2 |
| staff_balances | 1 |
| staff_incomes | 1 |
| rewards | 1 |
| penalties | 1 |
| staff_bank_accounts | 1 |
| withdrawals | 1 |
| wallet_transactions | 4 |
| finance_settings | 0 |
| payment_webhook_events | 1 |
| idempotency_records | 0 |
| outbox_events | 0 |

## ai_db

| Bảng | Dòng demo |
|---|---:|
| ai_conversations | 1 |
| ai_messages | 2 |
| booking_drafts | 1 |
| ai_tool_calls | 1 |
| knowledge_documents | 1 |
| knowledge_chunks | 1 |
| idempotency_records | 0 |

Refresh token, idempotency và outbox không được tạo giả: chỉ có dữ liệu khi triển khai luồng thật.
ID hiện là BIGINT tự tăng. Demo: Admin/User 1, Customer/User 2, Staff/User 3;
Category/Service/Package/AddOn đều có ID 1 trong bảng tương ứng; Booking 1, 2, 3;
Payment 1, 2; WalletTransaction 1, 2, 3, 4. Reference ID xuyên service đã được cập nhật đồng bộ.
ID chỉ duy nhất trong bảng/service sở hữu, không duy nhất trên toàn hệ thống.

Demo gồm một booking hoàn thành, một booking hủy/hoàn tiền, một booking đã gán Staff;
2 payment, 1 refund, 1 invoice, 1 earning, 1 withdrawal và 4 bút toán ví.
Ledger ví: 140000 + 10000 - 5000 - 40000 = 105000 VND, khớp available_balance.
Các phần trăm phí/rate và giá này chỉ phục vụ demo, không phải chính sách doanh nghiệp đã chốt.
