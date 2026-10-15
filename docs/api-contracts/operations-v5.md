# API nghiệp vụ đang triển khai theo V5

Cập nhật 14/10/2026. Phần này mô tả code hiện có; các contract dự thảo cũ cần đối chiếu với file này. Customer/Staff dùng mobile, Admin dùng web. Mỗi service vẫn sở hữu database và transaction riêng.

Prefix Gateway: `/api/v1/{identity|catalog|booking|finance|ai-assistant}`. ID entity là số BIGINT; adapter giao diện chuyển sang string. Các route được bảo vệ nhận `Authorization: Bearer <accessToken>` từ Identity, xác minh tài khoản còn ACTIVE và kiểm tra role tại service sở hữu. Không có đăng nhập bằng tài khoản fixture trong ứng dụng.

## Identity

| Method / path sau prefix identity | Quyền | Chức năng |
|---|---|---|
| GET /administrative-units/hcm | Public | Danh mục 168 phường/xã/đặc khu từ administrative_units, gồm id nội bộ/name/type; Nghị quyết 1685/NQ-UBTVQH15, hiệu lực 01/07/2025 |
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

Địa chỉ TP.HCM theo mô hình hai cấp gửi `ward` là tên đầy đủ chọn từ danh mục (ví dụ `Phường Sài Gòn`), `district` rỗng hoặc không gửi, `city="TP. Hồ Chí Minh"`. Identity kiểm tra ward thuộc danh mục trước lưu. V11 chỉ thêm dữ liệu tham chiếu chính thức, không đổi địa chỉ hiện có. Payload cũ có district vẫn được hỗ trợ để đọc/sửa tương thích. Khi sửa trên mobile, khách chủ động chọn lại ward mới; không suy ra ward mới từ quận cũ.

Booking ưu tiên ward mới; snapshot lịch sử chỉ có district vẫn đọc được. Staff mobile/Admin web chỉ chọn phường/xã/đặc khu từ danh mục Identity. Booking kiểm tra tên theo danh mục trước khi lưu vào staff_areas.ward; DTO areaIds chứa tên đầy đủ, các field district trong lịch/cơ hội/ca làm giữ tên field để tương thích. V7 ngừng dùng địa bàn quận cũ cho ghép việc mới; không suy ra phường mới từ quận cũ, không đổi snapshot ca lịch sử. Ca đã nhận vẫn được bảo vệ thời gian thực hiện.

### Đăng ký lịch tuần (15/10/2026)

- GET `/api/v1/booking/staff/me/availability`: `saved.days` gồm 14 ngày từ thứ Hai tuần hiện tại, `saved.registeredWeekStarts` liệt kê tuần đã xác nhận; giữ revision, areaIds và matchNotifications.
- PUT cùng endpoint: `{weekStart:"YYYY-MM-DD",days:[{date,intervals:[{startsAt,endsAt}]}],confirmedDates:["YYYY-MM-DD"],areaIds:["Phường Sài Gòn"],matchNotifications:true,revision:0}`. Gửi đủ 7 ngày của tuần hiện tại hoặc tuần kế tiếp. Người dùng chọn ca làm hoặc Nghỉ cho từng ngày còn lại; confirmedDates phải bao gồm đủ các ngày từ hôm nay trở đi trong tuần được lưu. Ngày đã xác nhận nghỉ có intervals rỗng. Xác nhận một lần cho cả tuần, không tự lặp lịch sang tuần mới.
- Chỉ thay các ngày thuộc tuần đã gửi; giữ lịch tuần khác và phần thời gian đã qua. Server so sánh độ bao phủ trước thời điểm lưu, không tin giờ máy khách. Khoảng rảnh đang diễn ra có thể thay phần tương lai nếu giữ nguyên phần đã qua. 08:00–20:00, bước 30 phút; toàn bộ ca đã nhận phải còn được bao phủ, kể cả khi hai khoảng liền nhau.
- 400: tuần/ngày/giờ/khu vực không hợp lệ; 409: revision cũ, sửa thời gian quá khứ hoặc làm mất khoảng rảnh/phường mới của ca đã nhận. Không commit một phần khi lỗi.
- Đăng ký lịch tuần là điều kiện đầu tiên trong eligibility dùng chung cho tìm nhân viên theo giờ khách đặt, tạo Mode A, matching và claim. Tuần chưa có bản ghi staff_schedule_weeks trả duy nhất SCHEDULE_REQUIRED, không xét tiếp các điều kiện khác. Sau đăng ký vẫn phải đủ năng lực, khu vực, trực tuyến, khoảng rảnh bao phủ ca và không trùng lịch. Đăng ký nghỉ cả tuần không tạo giờ rảnh để nhận đơn.
- Mobile hiển thị nhắc đăng ký các ngày còn lại khi Staff vào app hoặc quay lại app. Lưu đủ tuần thì không nhắc; sang tuần mới chưa đăng ký thì nhắc lại. Trên màn đăng ký không hiện modal nhắc để tránh cản thao tác.

### Khách chọn nhân viên và thời gian đặt trước (15/10/2026)

- Tìm nhân viên và POST bookings yêu cầu giờ bắt đầu cách thời điểm server hiện tại ít nhất 2 giờ. Giữ cửa sổ hôm nay + 6 ngày, 08:00–20:00 và bước 30 phút; toàn bộ thời lượng, kể cả add-on/dịch vụ kèm theo, phải hợp lệ. Kiểm tra lại trước khi ghi đơn. Không áp quy tắc 2 giờ cho Staff nhận một đơn đã được tạo trước đó; Staff vẫn phải nhận trước giờ bắt đầu.
- GET `/api/v1/booking/customer/staff?addressId=...&startsAt=...&durationMinutes=120`: serviceId là tùy chọn. Bỏ serviceId để trả tất cả nhân viên khả dụng có ít nhất một dịch vụ đang hoạt động và năng lực APPROVED còn hiệu lực. Nếu gửi serviceId thì chỉ trả nhân viên phù hợp dịch vụ đó. durationMinutes từ 30–720, chia hết cho 30; địa chỉ phải thuộc khách đăng nhập.
- Mỗi nhân viên khả dụng có `availableUntil` (ISO instant) và `availableUntilReason` (`SCHEDULE_END`, `OPERATING_HOURS`, `NEXT_BOOKING`). Giới hạn lấy từ khoảng rảnh đã lưu chứa giờ bắt đầu, tối đa 20:00 và trừ 60 phút di chuyển trước ca tiếp theo. Mobile dùng để giải thích popup khi tổng thời lượng tăng do đổi gói/thêm dịch vụ; backend vẫn kiểm tra lại đầy đủ trước khi tạo/nhận đơn.
- Điểm sao nhân viên trên kết quả tìm kiếm, hồ sơ công khai và snapshot khi nhận ca mặc định là 5 nếu chưa có đánh giá công bố; `reviewCount` vẫn bằng 0. Khi có đánh giá PUBLISHED/RESOLVED, dùng trung bình thực tế, không cộng thêm một đánh giá 5 sao mặc định vào phép tính. Thống kê quản trị theo kỳ giữ giá trị trung bình dữ liệu thực tế để không thay đổi điều kiện thưởng.
- Mỗi nhân viên trả thêm `eligibleServices:[{id,name}]`, competencies là tên dịch vụ phù hợp tại thời gian đang tìm, cùng rating/reviewCount/completedBookingsCount từ dữ liệu Booking. Không trả năng lực chưa duyệt, hết hạn hoặc dịch vụ đang bị hạn chế.
- Lịch tuần đã xác nhận, giờ rảnh bao phủ toàn bộ khoảng yêu cầu, phường/xã, trạng thái nhân viên, năng lực, hạn chế và xung đột/đệm di chuyển đều kiểm tra từ DB. Giữ đệm 60 phút giữa ca như chính sách hiện hành; không giả lập khoảng cách hoặc ETA.
- Mobile ban đầu hiển thị toàn bộ kết quả; các checkbox dịch vụ được gom từ eligibleServices, chọn nhiều dịch vụ nghĩa là nhân viên làm được ít nhất một dịch vụ đã chọn. Bộ lọc sao/tuổi vẫn dùng cùng danh sách. Khách chọn dịch vụ cụ thể từ năng lực nhân viên, rồi chọn gói; thay thời lượng sẽ kiểm tra lại.
- Mode A khóa cùng staff_schedule_settings với luồng lưu lịch/claim rồi xét lại điều kiện trước INSERT. Lời mời ASSIGNED giữ khung giờ, ngăn hai khách đồng thời đặt cùng nhân viên; lỗi cạnh tranh trả 409, không tạo đơn/phân công giả thành công. Idempotent requestKey tiếp tục trả đơn đã lưu. Nhân viên vẫn phải tự xác nhận lời mời.

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

Promotion public trả `serviceIds` (rỗng = tất cả dịch vụ chính), mức đơn tối thiểu, mức giảm/trần giảm, thời gian hiệu lực và lượt dùng. `usageLimit: null` là không giới hạn; API quản trị cũng nhận null để bảo toàn điều kiện này khi tạo/sửa. Phạm vi xét theo dịch vụ chính; phần giảm được tính trên tổng gói và mục thêm. Mobile chỉ hiển thị đã áp dụng sau khi quote-preview thành công, mã bị từ chối không làm mất báo giá cơ bản. Khi quay lại wizard, các trường của bước sau đích đến bị xóa; bản ghi đơn đã gửi vẫn được giữ và không bị tự hủy.

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


## Mode A: thời hạn phản hồi lời mời (Booking V8)

- Sau khi xác nhận giá, Booking lưu `staffResponseDeadline` (3 phút), `staffWaitState=WAITING`, `staffWaitRound=1`; `serverNow` dùng để đồng bộ đồng hồ client. Snapshot nhân viên được lưu ngay khi mời.
- Worker kiểm tra thời hạn mỗi giây; khóa cùng booking với thao tác nhận đơn. Lời mời hết hạn không thể claim, kể cả trước lượt chạy worker. Booking/assignment chuyển `CANCELLED` với lý do `Hết thời gian nhận đơn`, `staffWaitState=EXPIRED`. Cả hai bên nhận thông báo qua outbox Identity.
- Customer `POST /api/v1/booking/bookings/{id}/staff-response` body `{ "action": "EXTEND" | "CANCEL" | "STOP", "round": 1 }`. Chỉ chủ đơn được thao tác trên lượt hết hạn. `EXTEND` kiểm tra lại điều kiện/lịch của nhân viên, tạo assignment mới, tăng lượt và thời hạn thêm 3 phút từ lúc xác nhận; vết hủy assignment/lịch sử trước được giữ. Gửi lại cùng lượt không tạo lời mời trùng. `CANCEL` đóng lựa chọn tìm nhân viên khác và giữ đơn hủy trong quản lý đơn. `STOP` đóng lựa chọn với lý do khách hủy đặt, không tiếp tục, rồi client về trang chủ. Cả hai thao tác chỉ điều hướng sau khi server xác nhận lưu. Mobile tìm lại bằng ID đơn sở hữu: tải lại địa chỉ, thời gian, gói, addon, cấu hình, câu trả lời, ghi chú và mã giảm giá từ snapshot; mở thẳng danh sách nhân viên, đánh dấu/khóa chọn người vừa hết hạn. Báo giá, lịch rảnh và điều kiện mã được kiểm tra lại, không dùng lại quote hay request key của đơn cũ. Đơn đã được nhận không thể hủy qua endpoint này.
- `staffWaitState=CLOSED` khi nhân viên nhận, khách chọn không chờ hoặc đã qua giờ làm. Client chỉ hiện thẻ chờ với Mode A `STAFF_ASSIGNED/WAITING` hoặc `CANCELLED/EXPIRED`. Nhấn thẻ mở `/booking/waiting?id=...`; quay về không đặt lại đồng hồ.
- Staff assignments bao gồm lời mời hết hạn chưa từng nhận, trả thêm `cancellationReason`, `responseTimedOut`. Chỉ lịch sử/thông tin cần thiết, không coi lời mời hết hạn là thu nhập đã phát sinh. Web quản lý đơn đọc trạng thái/lý do và lịch sử từ API đang có.


## Mode A: giới hạn tổng thời lượng 4 giờ

Tạo đơn chọn nhân viên yêu thích giới hạn tổng thời gian gói chính + add-on + dịch vụ liên quan (đã làm tròn theo báo giá) tối đa 240 phút. Gói chính đủ 4 giờ không được thêm addon, kể cả addon 0 phút. Booking kiểm tra báo giá Catalog trước khi ghi booking, assignment hoặc dùng mã giảm giá; yêu cầu sai trả HTTP 400. Mode B giữ chính sách hiện tại. Mobile thông báo ngay khi chọn vượt giới hạn: giữ gói chính không thêm addon, hoặc đổi sang gói thấp hơn đủ thời gian rồi thêm mục vừa chọn. Không tạo gói giả nếu Catalog không có gói phù hợp; giới hạn lịch rảnh của nhân viên vẫn được kiểm tra.


## Staff: chi tiết đơn trước khi nhận và phân luồng Mode A/B

`GET /api/v1/booking/staff/opportunities/{id}` chỉ STAFF đang đủ điều kiện nhận ca mở; Mode A phải có lời mời ASSIGNED dành cho chính tài khoản đó. Đơn đã đóng/hết hạn/không đủ điều kiện trả 409, lời mời của người khác trả 404. Endpoint trả thêm `address` đầy đủ từ snapshot địa chỉ khách đã xác nhận, `notes`, `workSelections[{label,value}]`, `addOnsText`, `relatedServicesText`, `paymentMethod`, `paymentStatus`, `totalCustomerPaid`, `city`. Không mở số điện thoại khách trước khi nhận. Danh sách matching vẫn không gửi địa chỉ chi tiết/ghi chú/cấu hình riêng; chỉ bổ sung tổng tiền, thanh toán, tên addon/dịch vụ thêm và thành phố. Assignment trả cùng dữ liệu công việc/thanh toán từ snapshot sau khi nhận; địa chỉ có fallback ghép đường, phường/xã và tỉnh/thành khi bản ghi cũ thiếu fullAddress.

Mobile tách Khách chọn bạn (Mode A, đếm lời mời của chính Staff, kể cả đang cần bật online/khôi phục điều kiện) và Tìm việc (Mode B còn phù hợp). Nút Mode A đỏ và nhấp nháy chậm khi số lời mời >0, tôn trọng giảm chuyển động; không đánh dấu đọc/xóa notification chuông. Poll matching đang có vẫn mỗi 5 giây, không tạo poll mới cho từng nút. Chi tiết ca dùng API thật; nhận trực tiếp qua claim, giữ kiểm tra điều kiện ở server và chuyển sang tiến trình ca sau khi ghi assignment thành công. Tiền mặt không yêu cầu chờ thanh toán trước khi di chuyển/làm việc; hoàn tất không tự ghi có thu nhập trước quyết toán.


## Chat sau khi nhận ca và thông báo tin mới

Claim tạo conversation cùng transaction với assignment. Mobile làm mới hội thoại ngay sau claim, cho phép hai bên nhắn ở ca CONFIRMED/đang thực hiện và cho Staff đã nhận một vị trí khi đơn nhóm vẫn MATCHING. Hộp thư và các nút chat đang mount chia sẻ một polling theo phiên đăng nhập (4 giây, tạm ngừng nền); tab khách Tin nhắn hiển thị tổng unreadCountCustomer từ database. Mở hộp thư không tự đọc các cuộc trò chuyện khác; chỉ màn hình hội thoại đang focus mới tải/đánh dấu tin đã xem. Cache tài khoản và phản hồi cũ được chặn khi đổi tài khoản hoặc đã có read receipt mới.

Gửi TEXT tạo message và outbox CHAT_MESSAGE cùng transaction; lặp clientKey không thêm message/notification. Worker Identity chỉ gửi cho người nhận với eventKey chat-message:{messageId}, type CHAT_MESSAGE, data {bookingId,conversationId,messageId}. Không gửi nội dung chat riêng trong preview chuông. Mobile nhấn thông báo mở đúng /chat/[id]; server vẫn kiểm tra thành viên hội thoại. Chưa có push OS, luồng này là hộp thư/chuông trong ứng dụng đang chạy.

PATCH /conversations/{id}/read nhận body tùy chọn {throughMessageId:<id tin lớn nhất đã tải>} và trả {changed,unreadCount}. Giới hạn này giữ tin đến sau lần tải ở trạng thái chưa đọc. Client cũ không gửi body giữ hành vi đọc toàn bộ. Tin đã gửi của mình không cộng vào badge. Danh sách conversations gom lastMessage và hai bộ đếm trong một truy vấn SQL, không gọi thêm ba truy vấn cho từng hội thoại.
