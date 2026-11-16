# Số đơn hoàn tất của khách hàng

GET /api/v1/booking/customer/statistics yêu cầu Bearer token CUSTOMER và trả `{ "completedBookings": 0 }`.

Số đếm lấy trực tiếp từ bookings trong Booking DB, lọc customer_id theo tài khoản đã xác thực và status=COMPLETED. Endpoint không nhận ID khách hàng từ client. Không áp dụng giới hạn 500 đơn của API danh sách.

Màn Tài khoản mobile đọc số này khi mở màn, trở lại ứng dụng và mỗi 15 giây khi màn đang được xem. Khi tải lỗi, giao diện hiển thị trạng thái thử lại. Ô Điểm thưởng đã bỏ khỏi màn khách hàng.
