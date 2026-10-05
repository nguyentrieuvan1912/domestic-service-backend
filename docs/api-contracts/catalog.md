# catalog API contract — draft

Owner: Người 1. Consumer: Booking/AI.
Dự thảo: GET /api/v1/catalog/services, /services/{id};
POST /api/v1/catalog/quotes; Admin quản lý service/package/add-on/requirements/promotion.
Quote cần serviceId, lựa chọn package/add-on, các requirement đã validate,
currency VND, amount, duration, quoteVersion/expiresAt. Booking lưu snapshot.
Không dùng giá do frontend tự gửi làm nguồn tin cậy.

Đã triển khai: GET /api/v1/catalog/system/info trả {service, stage: "scaffold"}.
