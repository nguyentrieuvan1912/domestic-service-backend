# Identity API — đã triển khai cho Web

Owner: Người 1. Consumer hiện tại: domestic-service-web. Prefix `/api/v1/identity`; gọi qua Gateway 8080.
ID JSON là số Long/BIGINT. Ngày giờ ISO-8601 UTC. Lỗi HTTP 400/401/403/404/409 dạng `application/problem+json` với `detail`; validation thêm `errors`.

## Xác thực

| Method | Path | Quyền | Request / response |
|---|---|---|---|
| POST | /auth/register/email | Public | `{email}` → gửi mã Gmail, trả challengeId/expiresAt/resendAt |
| POST | /auth/register/verify | Public | `{challengeId,code}` → verificationToken sau xác thực |
| POST | /auth/register | Public | `{fullName,phone,verificationToken,password}` → HTTP 201 session |
| POST | /auth/login | Public | `{identifier,password}` → HTTP 200 session; identifier là email, số điện thoại hoặc user_name |
| GET | /auth/me | Bearer | `{id,fullName,phone,email,role,status}` |
| POST | /auth/logout | Bearer nếu có | HTTP 204; thu hồi token hiện tại; có thể gọi lại |

Session: `{accessToken,expiresAt,user:{id,fullName,phone,email,role,status}}`.
Đăng ký public luôn tạo CUSTOMER ACTIVE, bỏ qua role/status/ID do client gửi. Không có đăng ký public cho ADMIN/STAFF.
Email được trim/lowercase; số điện thoại `0` + 9 số hoặc `+84` + 9 số, lưu chuẩn `0` + 9 số.
Tên tối đa 160 ký tự; email tối đa 254; mật khẩu tối thiểu 8 ký tự, tối đa 72 byte UTF-8, BCrypt cost 12.
Email/số điện thoại trùng → 409; thông tin sai, tài khoản INACTIVE/BLOCKED → 401.

Token là 32 byte ngẫu nhiên, base64url, hạn 8 giờ. Database `account_sessions` chỉ lưu SHA-256 của token.
Gửi `Authorization: Bearer <accessToken>`; mỗi yêu cầu kiểm tra phiên chưa hết hạn và users.status ACTIVE.
Khóa/tạm ngưng xóa toàn bộ phiên; mở khóa không khôi phục phiên cũ. Logout xóa một phiên.
Response auth/admin có `Cache-Control: no-store`. Web lưu phiên ở sessionStorage riêng cho ADMIN/CUSTOMER.
**Đây là bearer session opaque, chưa phải JWT hoặc refresh token.** Không dùng token này để giả định Booking/Finance đã có security;
contract JWT liên service, issuer/audience/key rotation và refresh rotation cần thống nhất trước khi tích hợp các API ghi ở service khác.

## Khách hàng trên Admin

| Method | Path | Request / response |
|---|---|---|
| GET | /admin/customers | Array `{id,fullName,phone,email,status,statusNote,address,joinDate,totalBookings,totalSpent,recentBookings}` |
| PATCH | /admin/customers/{id}/status | `{status,statusNote}` → 204 |

Toàn bộ `/admin/**` bắt buộc ADMIN đang ACTIVE (thiếu/sai token → 401; CUSTOMER/STAFF → 403).
PATCH nhận ACTIVE/INACTIVE/LOCKED; LOCKED ánh xạ users.status BLOCKED. LOCKED bắt buộc statusNote, tối đa 1000 ký tự.
Không xóa khách hàng để giữ tham chiếu booking. Tổng booking lấy customers.total_bookings; địa chỉ lấy mặc định trong Identity.
`totalSpent=null` và `recentBookings=[]`: chưa nối Finance/Booking; Web hiển thị chưa có dữ liệu, không dùng mock.
Đây là danh sách toàn bộ cho màn hình quản trị hiện tại, chưa có phân trang server.

## Xét duyệt ứng viên và cấp tài khoản Staff

| Method | Path | Request / response |
|---|---|---|
| GET | /admin/candidates | Array hồ sơ ứng viên, id số, registeredAt ISO, staffId số hoặc null |
| POST | /admin/candidates | `{fullName,phone,email,cccd,birthYear,gender,area,source,services,note}` → 201 hồ sơ PENDING |
| PATCH | /admin/candidates/{id} | Chuyển giai đoạn theo thứ tự, trả hồ sơ đã lưu |
| POST | /admin/candidates/{id}/staff-account | `{password,rank}` → 201 hồ sơ với staffId |
| GET | /admin/staff | Array `{id,fullName,phone,identityCard,status,statusNote,rank,joinDate,averageRating}` |
| PATCH | /admin/staff/{id} | `{fullName,phone,status,statusNote,rank}` → 204 |

Luồng bắt buộc: PENDING → ONBOARDING → TRAINING → EVALUATION → admin duyệt và tạo Staff.

- POST hồ sơ: CCCD 12 chữ số; tuổi từ 18, birthYear >= 1940; gender Nam/Nữ/Khác; source Website/Google Forms/Zalo/Walk-in.
  services là danh sách tên dịch vụ mong muốn (snapshot từ Catalog trên Web), tối đa 50; không có nghĩa ứng viên đã có năng lực được Booking duyệt.
- PATCH sang ONBOARDING: `{status:"ONBOARDING"}`.
- PATCH sang TRAINING: thêm documentChecklist đủ `Bản sao CCCD`, `Hộ khẩu / KT3`, `Giấy khám sức khỏe`, và onboardingNote không rỗng.
- PATCH sang EVALUATION: thêm trainingScore 0..100 và trainingNotes không rỗng. Chưa tạo account ở bước này.
- Tạo Staff: chỉ EVALUATION chưa có staffId. Admin chọn rank INTERN/STANDARD/PROFESSIONAL và nhập mật khẩu khởi tạo.
  Email/phone/CCCD lấy từ hồ sơ đã lưu, không nhận từ request cấp account. Tài khoản STAFF ACTIVE, trạng thái hoạt động OFFLINE.
  approved_by/approved_at được ghi nhận. Hồ sơ users/staffs và liên kết candidate nằm trong một transaction;
  khóa hàng candidate ngăn hai yêu cầu đồng thời tạo trùng. Trùng email/phone/CCCD hoặc đã cấp → 409; rollback không tạo account dở dang.
- Web dùng email để bàn giao tài khoản; không giả báo đã gửi email. Mật khẩu không trả trong response và không lưu plaintext.
- birthYear giữ trong hồ sơ ứng viên; không suy diễn ngày/tháng sinh cho staffs.date_of_birth.
- account_rank là metadata xếp hạng tài khoản tuyển dụng. Năng lực Staff–Service, vùng hoạt động/lịch rảnh thuộc Booking;
  thu nhập/chi trả thuộc Finance. Web giữ bố cục tab và hiển thị chưa kết nối cho các mục chưa có API, không ghi dữ liệu giả.

## Bootstrap Admin

Không có endpoint public để tạo admin. Có thể cấu hình lần đầu bằng biến trong `.env` bị ignore:
`IDENTITY_BOOTSTRAP_EMAIL`, `IDENTITY_BOOTSTRAP_PHONE`, `IDENTITY_BOOTSTRAP_PASSWORD`.
Chỉ tạo khi chưa có ADMIN ACTIVE; không đổi password của admin đã có, không kích hoạt tài khoản demo.
Compose chỉ truyền ba biến vào Identity; bỏ biến sau setup và recreate Identity. Không commit mật khẩu.
Seed cũ vẫn INACTIVE và disabled password.

Migration V4 thêm status_note, staffs.account_rank, account_sessions và staff_candidates; không sửa V1/V2/V3.
Tests: AccountApiTests kiểm tra quyền/duplicate/expiry/logout/block, chặn bỏ bước, đủ giấy tờ/điểm, cấp tài khoản đồng thời,
rollback trùng contact và cập nhật/khóa Staff. Các endpoint scaffold/catalog hiện có không đổi.


## Đăng ký bằng Gmail đã xác thực (Web và Mobile)

Luồng bắt buộc: Gmail → mã 6 chữ số → họ tên/số điện thoại/mật khẩu → tài khoản CUSTOMER.

- `POST /api/v1/identity/auth/register/email`: `{ "email": "tenban@gmail.com" }`. Gửi email qua SMTP thật; trả `challengeId`, `email`, `expiresAt`, `resendAt`. Chỉ nhận @gmail.com. Gmail đã có tài khoản trả 409. SMTP chưa cấu hình/gửi thất bại trả 503, không tạo tài khoản hoặc giả lập gửi thành công.
- `POST /api/v1/identity/auth/register/verify`: `{ "challengeId": "...", "code": "012345" }`. Trả `verificationToken`, Gmail đã xác thực và `expiresAt`. Mã 10 phút, tối đa 5 lần sai, gửi lại sau 60 giây, tối đa 5 lần/giờ/Gmail. Gửi lại vô hiệu hóa mã và token xác thực trước.
- `POST /api/v1/identity/auth/register`: `{ "verificationToken": "...", "fullName": "Nguyễn Văn A", "phone": "0901234567", "password": "..." }`. Token hoàn tất hồ sơ có hiệu lực 20 phút và chỉ dùng một lần. Email luôn lấy từ database của token; client không thể đổi email. Tạo users + customers + session và tiêu thụ token trong cùng transaction; lỗi trùng số điện thoại không tiêu thụ token.
- `GET /api/v1/identity/auth/profile`: bearer session bắt buộc. Trả hồ sơ hiện tại từ users/customers/staffs, không nhận customerId từ client, không trả password hoặc mã/token xác thực. Có addressCount/defaultAddress, totalBookings/rewardPoints từ Identity; không giả lập số đơn đã hoàn tất từ Booking.
- `PATCH /api/v1/identity/customer/profile`: `{ "fullName": "...", "phone": "..." }`, bearer CUSTOMER bắt buộc. Chỉ sửa chính tài khoản trong phiên. Email đã xác thực không chỉnh trực tiếp.
- `PATCH /api/v1/identity/staff/profile`: `{ "phone": "...", "bio": "..." }`, bearer STAFF bắt buộc. Chỉ sửa chính tài khoản trong phiên; email/CCCD/họ tên xét duyệt không chỉnh trực tiếp.

Identity V5 thêm registration_verifications. Chỉ lưu BCrypt hash của mã và SHA-256 hash của token hoàn tất hồ sơ; không trả hoặc ghi log mã qua API. Mobile bỏ đăng nhập mẫu; session native dùng Expo SecureStore, web dùng sessionStorage. Hồ sơ đọc từ backend sau đăng nhập/khôi phục phiên.

Cấu hình gửi Gmail trong `.env` ignored: SMTP_HOST=smtp.gmail.com, SMTP_PORT=587, SMTP_USERNAME=<Gmail gửi>, SMTP_PASSWORD=<App Password>, IDENTITY_MAIL_FROM=<Gmail gửi>, SMTP_AUTH=true, SMTP_STARTTLS=true. Recreate Identity sau khi sửa: `docker compose up -d --build identity-service`. Không đặt SMTP password trong EXPO_PUBLIC_* hoặc VITE_*.
SMTP là dịch vụ tùy chọn khi khởi động, không nằm trong health liveness; endpoint gửi mã kiểm tra và báo lỗi thật nếu gửi thất bại.
# Hồ sơ nhân viên: ngày sinh và avatar (15/10/2026)

- Form ứng tuyển mới gửi dateOfBirth dạng YYYY-MM-DD và experienceYears; backend kiểm tra đủ 18 tuổi và suy ra birthYear. Payload cũ chỉ có birthYear vẫn được tiếp nhận, không tự tạo ngày/tháng sinh.
- Cấp tài khoản từ ứng viên chuyển ngày sinh/năm sinh, giới tính, kinh nghiệm và giới thiệu sang staffs. Admin POST/PATCH staff hỗ trợ dateOfBirth, birthYear, gender, experienceYears, bio. Hồ sơ cũ có thể bổ sung ngày sinh tại Admin; bỏ trống trường tùy chọn trong API không xóa thông tin đã có.
- V12 thêm staffs.birth_year và lấy lại năm sinh từ staff_candidates của tài khoản đã cấp. Không dùng 01/01 làm ngày sinh giả.
- GET auth/profile, staff-directory và admin/staff trả dateOfBirth là chuỗi ngày lịch YYYY-MM-DD (không timestamp UTC), birthYear là số hoặc null. Directory chỉ gồm tài khoản đang hoạt động đã được cấp/duyệt.
- Avatar mặc định của Staff lưu ở users.avatar_url là /api/v1/identity/staff-avatar.png, ảnh PNG công khai do Identity phục vụ. Profile, directory và màn quản trị dùng cùng giá trị; mobile/web ghép đường dẫn tương đối với API_BASE_URL. Ảnh đã có được giữ nguyên.
- Customer không hiển thị nhãn CCCD/xác minh lặp lại. Khi chỉ có năm sinh, hiển thị năm sinh; khi không có dữ liệu, hiển thị chưa cập nhật thay vì 0 tuổi. Không bổ sung số CCCD hoặc thông tin liên hệ vào API directory công khai.
