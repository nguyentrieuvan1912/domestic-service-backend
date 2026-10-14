# API nghiệp vụ đang triển khai theo V5

Cập nhật 14/10/2026. Phần này mô tả code hiện có; các contract dự thảo cũ cần đối chiếu với file này. Customer/Staff dùng mobile, Admin dùng web. Mỗi service vẫn sở hữu database và transaction riêng.

Prefix Gateway: `/api/v1/{identity|catalog|booking|finance|ai-assistant}`. ID entity là số BIGINT; adapter giao diện chuyển sang string. Các route được bảo vệ nhận `Authorization: Bearer <accessToken>` từ Identity, xác minh tài khoản còn ACTIVE và kiểm tra role tại service sở hữu. Không có đăng nhập bằng tài khoản fixture trong ứng dụng.

## Identity

| Method / path sau prefix identity | Quyền | Chức năng |
|---|---|---|
| GET /staff-directory | Public | Thông tin hiển thị Staff đã được duyệt; không trả điện thoại, email, CCCD |
| PATCH /staff/status | STAFF | `{isOnline:boolean}`; không bật nhận việc nếu bị đình chỉ/hạn chế |
| POST /admin/staff | ADMIN | Cấp tài khoản sau tuyển dụng trực tiếp; fullname/phone/email/password/identityCard/rank |
| GET, POST /customer/addresses; GET, PUT, DELETE /customer/addresses/{id} | CUSTOMER | Địa chỉ thuộc tài khoản xác thực |
| PATCH /customer/addresses/{id}/default | CUSTOMER | Đặt địa chỉ mặc định với khóa chống hai địa chỉ mặc định |
| GET /customer/favorites; PUT /customer/favorites/{serviceId} | CUSTOMER | `{favorite:boolean}`; xác minh dịch vụ qua Catalog |
| GET /notifications; GET /notifications/{id}; PATCH /notifications/{id}/read; PATCH /notifications/read-all | Tài khoản sở hữu | Danh sách/đánh dấu đọc; thao tác đọc lặp không tăng số bản ghi |
| POST /support/requests | Public | Lưu yêu cầu liên hệ thực tế |
| GET /admin/support-requests; PATCH /admin/support-requests/{id} | ADMIN | RECEIVED, IN_PROGRESS, RESOLVED; adminNote |
| GET /community/posts | Public hoặc tài khoản | Bài công bố và bài riêng của người đăng |
| POST /community/posts; POST /community/posts/{id}/comments; PATCH /community/posts/{id}/like | Tài khoản | Nội dung văn bản; `{liked:boolean}` idempotent |
| GET /admin/community/posts; PATCH /admin/community/posts/{id} | ADMIN | PENDING, PUBLISHED, HIDDEN |
| POST /internal/events | X-Service-Key | Sự kiện nội bộ; khóa riêng từ INTERNAL_SERVICE_TOKEN |

Đăng ký Customer giữ hợp đồng Gmail → mã 6 chữ số → verificationToken → hồ sơ/mật khẩu. Staff không tự đăng ký. Bài Customer/Staff mới chờ duyệt; Admin có thể đăng thông báo. Media chưa có storage nên trả 503 khi đăng kèm tệp, không ghi URL file cục bộ giả.

## Catalog

| Method / path | Quyền | Chức năng |
|---|---|---|
| GET /categories, /services, /services/{id}, /promotions | Public | Danh mục/gói/add-on/câu hỏi/khuyến mãi trong CSDL |
| GET /admin/snapshot | ADMIN | Danh mục, dịch vụ, gói, add-on cho các tab quản lý |
| POST /admin/catalog/{categories|services|packages|addons}; PUT /admin/catalog/{kind}/{id} | ADMIN | Tạo/cập nhật và trạng thái; dynamicFields đồng bộ câu hỏi dịch vụ |
| GET, POST /admin/promotions; PUT /admin/promotions/{id} | ADMIN | Quản lý lịch hiệu lực, giảm giá, giới hạn lượt dùng |
| POST /quote-preview | CUSTOMER | Tính và kiểm tra giá trên server; không dùng giá client gửi |
| POST /quotes | CUSTOMER | Quote lưu trong CSDL, hết hạn sau 10 phút |
| POST /quotes/{id}/consume | CUSTOMER sở hữu hoặc ADMIN | Đối chiếu booking đã commit; tăng lượt promo một lần |

Quote request gồm serviceId, packageId, requiredStaffCount, quantity (ITEM), addOnIds, relatedServiceIds, promotionCode, answers, configuration và requestKey khi lưu. `answers` dùng đúng fieldKey của Catalog; hỗ trợ TEXT, NUMBER, SELECT, MULTI_SELECT, BOOLEAN, DATE. Configuration giữ thông số của form cũ với nhãn hiển thị; không thay thế các câu trả lời bắt buộc. ID gói/add-on phải thuộc dịch vụ.

Giá hiện tại: giá gói × số lượng ITEM × ceil(số Staff / Staff mặc định của gói), cộng hạng mục bổ sung rồi trừ promo hợp lệ. Thời lượng tính từ gói/số lượng/add-on và làm tròn 30 phút. Đây là quy tắc ban đầu cần kiểm tra lại từng dịch vụ, không phải mức lương theo cấp bậc.

## Booking

| Method / path | Quyền | Chức năng |
|---|---|---|
| POST /bookings | CUSTOMER | Tạo đơn từ quote và địa chỉ sở hữu; requestKey chống trùng |
| GET /bookings, /bookings/{id}, /bookings/{id}/history | Theo scope | Customer sở hữu; Staff đã nhận ca; Admin tất cả |
| POST /bookings/{id}/cancel | CUSTOMER sở hữu hoặc ADMIN | Hủy ở trạng thái cho phép, giải phóng slot/đóng chat; không giả hoàn tiền |
| GET /customer/staff | CUSTOMER | serviceId/addressId/startsAt/durationMinutes, lọc Staff phù hợp |
| GET, PUT /staff/me/availability | STAFF | Lịch rảnh/khu vực/revision; không bỏ ca đã nhận |
| GET /staff/me/capabilities | STAFF | Năng lực và hạn chế của chính mình; ẩn ghi chú nội bộ |
| GET, PUT /admin/staff/{id}/operations | ADMIN | Phê duyệt năng lực, khu vực, hạn chế; GET hỗ trợ period ALL/CURRENT/PREVIOUS |
| GET /staff/me/matching, /staff/me/assignments | STAFF | Cơ hội đã lọc và ca đã nhận |
| POST /staff/opportunities/{id}/claim, /reject | STAFF | Khóa slot/vị trí trước khi nhận, từ chối lời mời Mode A |
| PATCH /staff/assignments/{id}/status | STAFF sở hữu | ACCEPTED → EN_ROUTE → IN_PROGRESS → COMPLETED; hủy có lý do |
| PATCH /staff/assignments/{id}/checklist | STAFF sở hữu | `{id,done:boolean}`; checklist từ workflow dịch vụ đã lưu |
| POST /bookings/{id}/incidents | Thành viên hợp lệ | Lưu sự cố thật |
| GET /admin/incidents; PATCH /admin/incidents/{id} | ADMIN | OPEN/RESOLVED |
| GET /reviews; POST /bookings/{id}/reviews | Theo role | Đánh giá sau hoàn tất, đúng Staff đã hoàn thành |
| PATCH /admin/reviews/{id} | ADMIN | PUBLISHED/HIDDEN/RESOLVED, ghi chú nội bộ |
| GET /public/review-stats, /public/services/{id}/reviews, /public/staff-stats, /public/staff/{id}/reviews | Public | Thống kê/nhận xét công bố; không lộ thông tin liên hệ |
| GET /conversations, /conversations/{id}/messages | CUSTOMER/STAFF thành viên | Hội thoại phát sinh sau nhận ca |
| POST /conversations/{id}/messages; PATCH /conversations/{id}/read | Thành viên | `{content,clientKey}`; gửi lặp không tạo hai tin |
| GET /admin/statistics?year=2026&period=year/month/q1..q4 | ADMIN | Thống kê theo CSDL |
| POST /admin/bookings/{id}/verify-payment | ADMIN | Đối chiếu số tiền thực tế từ Finance |

Giờ phục vụ 08:00–20:00, bước 30 phút, hôm nay đến hôm nay+6 theo Asia/Ho_Chi_Minh. Eligibility kiểm tra tài khoản online/ACTIVE, năng lực có hiệu lực suốt ca, hạn chế giao với ca, tỉnh/thành và quận đã lưu, lịch rảnh, đệm 1 giờ và vị trí còn trống. Không tính khoảng cách giả.

Mode A chỉ chọn một Staff và phải chờ Staff chấp nhận; gói nhiều Staff dùng Mode B. Nhận vị trí cuối có row lock và reservation constraint. Mode B chỉ CONFIRMED khi đủ người. Snapshot Staff và checklist được lưu khi nhận. Staff chưa chấp nhận lời mời không được đọc địa chỉ/điện thoại/lịch sử đơn qua route chi tiết hay danh sách.

Chat chỉ cho thành viên; đơn kết thúc đóng gửi tin. Không có phản hồi chat/nhận việc bằng timer. Tạo booking dùng bước xác nhận quote giữa hai service: booking commit với pricingConfirmed=false, consume quote, rồi mới bật matching. Bản ghi chưa xác nhận không được Staff nhận; outbox pending được giữ để xử lý nếu service gián đoạn.

## Finance

| Method / path | Quyền | Chức năng |
|---|---|---|
| GET /staff/me/wallet, /staff/me/bank-accounts | STAFF | Số dư, ledger, thu nhập, yêu cầu rút và ngân hàng sở hữu |
| POST /staff/me/bank-accounts | STAFF | Thêm ngân hàng ở PENDING |
| POST /staff/me/withdrawals | STAFF | amount/bankAccountId/requestKey; giữ tiền AVAILABLE → RESERVED |
| GET /admin/staff/{id}/wallet, /admin/bank-accounts, /admin/withdrawals, /admin/transactions | ADMIN | Đối soát CSDL |
| PATCH /admin/bank-accounts/{id} | ADMIN | VERIFIED/INACTIVE sau kiểm tra |
| PATCH /admin/withdrawals/{id} | ADMIN | APPROVED/REJECTED; từ chối trả khoản giữ; không cho tự gán PAID |
| PATCH /admin/transactions/{PAYMENT|REFUND}/{id} | ADMIN | Chỉ ghi chú; không tự đổi trạng thái tiền |
| GET, PUT /admin/policy | ADMIN | Phí nền tảng 0–100%, cần lưu chính sách trước quyết toán |
| POST /admin/bookings/{id}/cash-receipt | ADMIN | `{cashReceived:true}`: xác nhận đã thực nhận tiền mặt |
| POST /admin/bookings/{id}/settle | ADMIN | Chỉ COMPLETED, đủ Staff, đủ thanh toán đã xác minh; phát hành invoice/earning một lần |
| GET /invoices | CUSTOMER sở hữu hoặc ADMIN | Chứng từ quyết toán thực tế |
| POST /admin/staff/{id}/rewards, /penalties | ADMIN | Đề xuất PENDING; thưởng theo CURRENT/PREVIOUS và dữ liệu kỳ thực tế |
| GET /admin/adjustments; POST /admin/adjustments/{REWARD|PENALTY}/{id}/apply | ADMIN | Phê duyệt ghi sổ idempotent; không trừ vượt số dư |
| GET /admin/statistics?year=2026 | ADMIN | Dòng tiền đã xác nhận/quyết toán; khoản rút chờ duyệt không tính đã chi |
| POST /bookings/{id}/payment | CUSTOMER sở hữu | 503: chưa có adapter PayOS/VietQR |

Tiền mặt không tự PAID khi Customer chọn phương thức. Thu nhập không tự tăng khi Staff nhận/hoàn thành ca. Ledger append-only. Thưởng hiện có các mốc giao diện 150 ca/20 triệu/4.9 sao với số tiền 500k/1 triệu/300k; chờ Admin phê duyệt, cần rà chính sách trước sử dụng chính thức. Mức lương theo cấp bậc chưa được cấu hình, giao diện không dùng 80k/110k/150k mẫu.

QR/webhook, xác minh chuyển tiền và refund qua nhà cung cấp chưa triển khai; endpoint/UI chặn thao tác xác nhận giả. Hóa đơn hiện là chứng từ trong ứng dụng, chưa tích hợp hóa đơn thuế.

## AI và voice

AI có GET/POST `/conversations`, GET/POST `/conversations/{id}/messages`, DELETE `/conversations/{id}`. Scope CUSTOMER sở hữu, clientKey chống lặp; đóng hội thoại lưu ENDED. Khi chưa có AI_PROVIDER_URL và AI_PROVIDER_KEY, gửi tin trả 503 và không ghi phản hồi giả. Adapter riêng nhận `{messages,instructions}`, trả `{content}`; không phải URL API trực tiếp của một vendor bất kỳ. Chưa hoàn thiện intent/slot/RAG/tool calling/BookingDraft. Chưa tích hợp LiveKit/voice vào development build.

## Setup và giới hạn

- INTERNAL_SERVICE_TOKEN là secret riêng trong `.env`, truyền cho Identity/Booking/Finance. Sự kiện được lưu outbox, polling 5 giây, tối đa 20 lần thử; notification có eventKey chống trùng. Không dùng tài khoản mẫu để gửi sự kiện.
- Booking list/incident/support có giới hạn 500; community list 100, public review 100. Cần pagination và realtime push khi lượng dữ liệu tăng.
- Chính sách hủy/hoàn, lương theo cấp bậc, giá riêng từng loại thiết bị và trọng số recommendation/CP-SAT còn cần xác nhận. Không suy ra các mức phí/giảm giá bằng thông báo thành công trên client.
- Đăng ký Gmail và gửi mail thật dùng cấu hình SMTP hiện có; cần kiểm tra lại Gmail nhận OTP khi kiểm thử người dùng.
- Báo cáo/checklist tổng hợp nằm ở `E:/KhoaLuanTN/outputs/api-v5-integration/`.
