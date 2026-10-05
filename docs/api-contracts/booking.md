# booking API contract — draft

Owner: Người 2. Consumer: AI/Finance/Mobile.
Dự thảo: POST /api/v1/booking/previews, /bookings;
GET /api/v1/booking/bookings/{id};
POST /api/v1/booking/bookings/{id}/accept, /cancel; cập nhật tiến độ;
API availability/area/capability Staff + Admin duyệt.
Chốt Mode A/B, status enum, slot hold/expiry, chống trùng lịch và Idempotency-Key.
Catalog xác minh quote; Finance sở hữu payment/refund/earning.
Booking không được xác nhận thanh toán chỉ theo response của frontend.

Đã triển khai: GET /api/v1/booking/system/info trả {service, stage: "scaffold"}.
