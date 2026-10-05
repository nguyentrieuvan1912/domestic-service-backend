# Database inventory

Nguồn schema: ClassDiageamV3.png + model Mobile + nghiệp vụ QR/ví/AI đã chốt.
Đã áp dụng vào PostgreSQL local ngày 2026-10-05.

Có 53 bảng domain/kỹ thuật và 61 dòng demo; chưa tính service_schema_metadata và flyway_schema_history
(2 bảng hạ tầng trong mỗi database). 47 bảng có demo; 6 bảng vận hành để trống có chủ ý.
Không có dữ liệu người dùng/ngân hàng thật. Tài khoản demo không đăng nhập được.

| Database | Bảng domain/kỹ thuật | Dòng demo |
|---|---:|---:|
| identity_db | 7 | 8 |
| catalog_db | 8 | 8 |
| booking_db | 15 | 17 |
| finance_db | 16 | 21 |
| ai_db | 7 | 7 |

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

## catalog_db

| Bảng | Dòng demo |
|---|---:|
| service_categories | 1 |
| services | 1 |
| service_packages | 1 |
| add_ons | 1 |
| service_requirements | 1 |
| promotions | 1 |
| promotion_services | 1 |
| price_quotes | 1 |

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
ID demo cố định dùng chung giữa các database: user/customer/staff 10000000-..., Catalog 20000000-...,
Booking 30000000-..., Finance 40000000-..., AI 50000000-.... ID là UUID hợp lệ; prefix chỉ để nhận diện fixture.

Demo gồm một booking hoàn thành, một booking hủy/hoàn tiền, một booking đã gán Staff;
2 payment, 1 refund, 1 invoice, 1 earning, 1 withdrawal và 4 bút toán ví.
Ledger ví: 140000 + 10000 - 5000 - 40000 = 105000 VND, khớp available_balance.
Các phần trăm phí/rate và giá này chỉ phục vụ demo, không phải chính sách doanh nghiệp đã chốt.
