# finance API contract — draft

Owner: Người 1. Consumer: Booking.
Dự thảo: POST /api/v1/finance/payments, /refunds;
GET /api/v1/finance/payments/{id}, /staff/me/wallet;
POST /api/v1/finance/withdrawals; webhook từ nhà cung cấp.
Chốt provider QR, webhook signature, currency VND, amount lấy từ Booking snapshot,
idempotency, trạng thái thanh toán/hoàn tiền/withdrawal và reconciliation.
Không tự chuyển tiền thật khi user mới tạo yêu cầu withdrawal.
Earning ledger cần bút toán bất biến và chống ghi nhận một booking nhiều lần.

Đã triển khai: GET /api/v1/finance/system/info trả {service, stage: "scaffold"}.
