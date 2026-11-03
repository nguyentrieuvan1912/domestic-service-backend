# Tự động ghi thu nhập khi hoàn tất ca

Booking sở hữu trạng thái thực hiện, ảnh trước/sau và xác nhận thu tiền của từng assignment. Finance sở hữu ví, khoản trừ nhận đơn tiền mặt, payment, hóa đơn và thu nhập. Luồng mới không cần thao tác thu tiền/quyết toán từ admin.

- `PATCH /api/v1/booking/staff/assignments/{bookingId}/status`: khi `status=COMPLETED`, vẫn bắt buộc ít nhất một ảnh BEFORE và một ảnh AFTER thuộc đúng assignment. Với CASH, body phải thêm `cashReceived: true`; Booking lưu `cash_received_at`. Database cũng chặn hoàn thành CASH thiếu xác nhận.
- CASH: nhận ca yêu cầu đủ ví, Finance trừ phần tiền ca ngay khi nhận. Đơn nhiều nhân viên chia đều tổng tiền, phần dư 1đ phân cho các vị trí đầu; khoản chính xác được lưu ở `cash_collection_amount` và trả qua `cashCollectionAmount`. Nhân viên thu đúng phần này từ khách. Khi tất cả hoàn thành, Finance cộng `floor(phần tiền * 80 / 100)` vào từng ví, phần còn lại là phí nền tảng.
- BANK_TRANSFER_QR / WALLET: chỉ ghi thu nhập sau khi Finance có đủ payment đã xác minh, trừ các refund đã xử lý. Không coi QR được tạo là đã thu tiền. Thu nhập tự chia 80% theo phần việc khi tất cả nhân viên hoàn thành; không trừ ví nhân viên lúc nhận ca.
- Thu nhập, hóa đơn, ví và ledger được ghi cùng một transaction Finance; khóa theo booking và unique ledger key ngăn ghi trùng khi retry/concurrent request. Các hóa đơn đã ghi từ luồng cũ giữ nguyên.
- Booking lưu `finance_sync_required`, thời điểm kiểm tra và lỗi retry. Worker tự gửi snapshot đã khóa tới Finance mỗi 3 giây (tối đa 30 đơn/lượt, xoay vòng theo lần kiểm tra). Nếu Finance gián đoạn sau khi Booking hoàn thành, đơn vẫn được ghi nhận hoàn thành và tiền chờ tự ghi sổ, không hiển thị thông báo giả đã cộng ví.
- Khi Finance trừ thành công nhưng giao dịch nhận ca Booking rollback/mất phản hồi, lần reconcile CASH tiếp theo hoàn toàn bộ khoản trừ nếu assignment không còn ACCEPTED/COMPLETED. Khóa hàng Booking giữ xuyên request Finance để tránh hoàn khoản trừ đồng thời với nhận ca. Hủy đơn/ca trước khi làm cũng hoàn toàn bộ khoản trừ cho nhân viên, độc lập với hoàn ví khách.

Giao tiếp nội bộ yêu cầu `X-Service-Key` khớp `INTERNAL_SERVICE_TOKEN`, từ Booking sang Finance:

- `POST /api/v1/finance/internal/job-settlement/reserve/{staffId}`: snapshot booking gồm các assignments sau khi nhận ca; trả `{amount}` chỉ khi debit đã commit.
- `POST /api/v1/finance/internal/job-settlement/reconcile`: snapshot booking sau khi khóa; trả `WAITING` nếu chưa hoàn thành, hoặc `{invoiceId,status:PAID}` sau khi payment/invoice/income/ledger đã commit.

Các endpoint admin `.../cash-receipt` và `.../settle` đã được bỏ. Admin xem ảnh minh chứng, giao dịch, hóa đơn và trạng thái chờ hệ thống ghi sổ; không xác nhận thay nhân viên.

Đơn cũ đã hoàn thành không được tự dựng xác nhận thu tiền hoặc tự trừ ví hồi tố. Ca CASH đang hoạt động chưa có khoản trừ sẽ được kiểm tra/trừ ví trước khi cho phép xác nhận hoàn thành. Khi thiếu số dư hoặc Finance không sẵn sàng, API trả lỗi và giữ nguyên tiến độ ca.
