# Ghi chú đồng bộ sơ đồ Class với database

Nguồn đọc: **ClassDiageamV3.png** ở root workspace. File ClassDiagramV4.png hiện chứa Use Case Diagram,
nên không dùng làm nguồn class. Không sửa hình PNG/VPP gốc trong lần triển khai này.

Đã tạo migration V2/V3 riêng trong 5 service và seed local riêng bằng profile dev-seed.
Schema là bản triển khai ban đầu để hai người viết entity/API; không có nghĩa các method trong class đã được code.

## Ánh xạ các class hiện có

| Class trong V3 | Database | Bảng |
|---|---|---|
| User, Admin, Customer, Staff | identity_db | users, admins, customers, staffs |
| Address, Notification | identity_db | addresses, notifications |
| ServiceCategory, Service | catalog_db | service_categories, services |
| ServicePackage, AddOn, ServiceRequirement, Promotion | catalog_db | service_packages, add_ons, service_requirements, promotions |
| Booking, BookingAddOn, BookingRequirementAnswer | booking_db | bookings, booking_add_ons, booking_requirement_answers |
| BookingAssignment, BookingStatusHistory, Review | booking_db | booking_assignments, booking_status_histories, reviews |
| StaffArea, StaffAvailability, StaffServiceCapability, StaffRestriction | booking_db | staff_areas, staff_availabilities, staff_service_capabilities, staff_restrictions |
| Conversation, Message | booking_db | conversations, messages |
| Order, OrderDetail, Payment, Refund | finance_db | orders, order_details, payments, refunds |
| StaffBalance, StaffIncome, Reward, Penalty | finance_db | staff_balances, staff_incomes, rewards, penalties |
| AIConversation, AIMessage | ai_db | ai_conversations, ai_messages |

Mọi class của V3 có bảng tương ứng. Các method như login/createBooking/makePayment/calculateIncome
là hành vi nghiệp vụ, không chuyển thành column và chưa triển khai trong scaffold.

## Class nghiệp vụ cần bổ sung vào sơ đồ

| Class mới | Thuộc tính chính | Quan hệ cần vẽ |
|---|---|---|
| StaffBankAccount | id, staffId, bankCode, accountNumber, accountHolder, status, isDefault | Staff 1 → 0..* StaffBankAccount |
| Withdrawal | id, staffId, balanceId, bankAccountId, bankAccountSnapshot, amount, status, requestedAt, approvedBy, approvedAt, paidAt, transferReference, rejectionReason | StaffBalance 1 → 0..* Withdrawal; StaffBankAccount 1 → 0..* Withdrawal |
| WalletTransaction | id, balanceId, direction, bucket, transactionType, amount, referenceId, idempotencyKey, reversesTransactionId, createdAt | StaffBalance 1 → 0..* WalletTransaction; transaction có thể đảo một transaction trước |
| Invoice | id, orderId, bookingId, customerId, invoiceNumber, customerSnapshot, serviceSnapshot, subtotal, discountAmount, vatAmount, totalAmount, status, issuedAt | Order 1 → 0..1 Invoice |
| InvoiceItem | id, invoiceId, name, quantity, unitPrice, totalPrice | Invoice 1 → 0..* InvoiceItem trong DB; khi phát hành hợp lệ phải có ≥1 item |
| PriceQuote | id, customerId, serviceId, packageId, snapshot, baseAmount, addOnAmount, discountAmount, totalAmount, quoteVersion, expiresAt | Customer/Service → 0..* PriceQuote; Booking tham chiếu 0..1 quote và giữ snapshot |
| StaffReservation | id, staffId, bookingId, holdToken, startsAt, endsAt, expiresAt, status | Staff 1 → 0..* reservation; reservation tham chiếu 0..1 Booking (hold trước create) |
| BookingDraft | id, conversationId, customerId, draftData, missingSlots, previewSnapshot, quoteId, bookingId, status, version, expiresAt, confirmedAt | AIConversation 1 → 0..* BookingDraft; draft tham chiếu 0..1 Booking |
| PromotionService | promotionId, serviceId | Promotion ↔ Service là N:N khi giới hạn khuyến mãi theo dịch vụ |

WalletTransaction là nguồn đối soát ví; StaffBalance là bảng tổng hợp/cache cần cập nhật cùng transaction.
Rút tiền có bước giữ tiền (reservedBalance), phê duyệt và xác nhận chuyển khoản; không coi tạo Withdrawal là đã chuyển tiền.
Order giữ bảng quyết toán booking (gồm chi phí phát sinh); Invoice giữ bản chứng từ/snapshot.
Đây là invoice nội bộ, chưa tích hợp hóa đơn điện tử thuế.

## Class kỹ thuật có thể đặt vào sơ đồ bổ sung

- RefreshToken ở Identity: chỉ lưu tokenHash, expiresAt, revokedAt.
- PaymentWebhookEvent ở Finance: provider/eventId, payload, signatureVerified, status; chống xử lý webhook lặp.
- IdempotencyRecord ở Booking/Finance/AI: actorId + operation + key duy nhất, requestHash, response, status.
- OutboxEvent ở Booking/Finance: sự kiện giao dịch cần gửi/retry. Chưa có worker/broker thực thi.
- AIToolCall, KnowledgeDocument, KnowledgeChunk ở AI: audit tool calling và nguồn RAG.
  searchVector là tìm kiếm text PostgreSQL; embedding JSONB là chỗ giữ dữ liệu tạm, chưa có vector index/provider.

Các class kỹ thuật không bắt buộc nhét vào sơ đồ domain tổng quát; có thể tách diagram persistence/infrastructure.

## Thuộc tính và kiểu dữ liệu cần chỉnh

1. **ID giữ Long theo sơ đồ gốc.** Database hiện dùng BIGINT và primary ID tự tăng.
   V3 chuyển dữ liệu UUID demo trước đây thành 1, 2, 3...; không cần sửa sơ đồ sang UUID.
   Admin/Customer/Staff dùng chung ID User, không có bộ đếm riêng.
   API trả ID dạng số; Mobile string ID cần adapter khi tích hợp API.
2. **Tiền VND: Decimal → Long/BIGINT** cho giá, amount, balance, fee, tip, refund, reward, penalty.
   Rate/rating/score/hours vẫn Numeric. Promotion discountValue hiện là số nguyên
   (phần trăm nguyên hoặc số VND theo discountType); nếu cần phần trăm lẻ thì mở rộng bằng migration sau.
3. **LocalDateTime → OffsetDateTime/Instant** cho audit/payment/status/timestamp; SQL TIMESTAMPTZ lưu thời điểm.
   LocalDate/LocalTime vẫn hợp lệ cho ngày sinh và lịch rảnh theo giờ địa phương.
4. **Booking**: bookingDate/startTime/endTime được lưu bằng startsAt/endsAt + timezone;
   bổ sung serviceSnapshot, addressSnapshot, quoteId/quoteVersion, currency, promotionId/code, notes,
   cancelledBy/cancelledByType, version. Ngày/giờ hiển thị được suy ra theo timezone.
   Staff là tập BookingAssignment, không thêm một staffId duy nhất vào Booking.
5. **ServiceRequirement**: bổ sung fieldKey, label, validationRules; options chuyển String → JSON array.
   BookingRequirementAnswer bổ sung fieldKey/labelSnapshot, answerValue chuyển String → JSON value
   để giữ được number/boolean/list.
6. **Service/ServicePackage/AddOn**: duration/extraDurationHours được chuẩn hóa phút;
   bổ sung basePrice/image/highlights/workflow/benefits phục vụ model Mobile.
   package thêm maxArea; Service giữ version cho thay đổi giá.
7. **Address**: bổ sung title, latitude, longitude, updatedAt; district để nullable.
   Mỗi Customer có tối đa một address isDefault=true.
8. **StaffAvailability**: availableDate hoặc dayOfWeek (0=CN..6=T7), phải chọn đúng một kiểu,
   thêm timezone; startTime/endTime bắt buộc end > start. Ca qua đêm cần tách hai ca hoặc migration sau.
9. **StaffBalance**: balance chung được tách availableBalance, pendingBalance, reservedBalance;
   bổ sung totalEarned, withdrawnAmount, version. MinimumBalance là ngưỡng cảnh báo.
10. **StaffIncome**: thêm balanceId, status, settledAt; công thức staffAmount = serviceAmount - platformFee + tipAmount.
11. **Payment**: thêm bookingId/customerId/orderId, provider, providerReference, qrContent, expiresAt,
    currency, updatedAt; method chuẩn BANK_TRANSFER_QR hoặc CASH.
    VNPAY/MOMO/ZALOPAY trong Mobile hiện là mock cũ, không được dùng làm enum mặc định cho luồng QR chuyển khoản.
12. **Refund**: thêm approvedBy/adminNote; status có PENDING/APPROVED/PROCESSING/PROCESSED/REJECTED/FAILED.
    transactionCode và completedAt là kết quả sau khi hoàn tiền thành công.
13. **Notification/Message**: isRead là thuộc tính suy ra từ readAt != null, không lưu hai giá trị song song.
14. **AIMessage**: senderType chuẩn USER/ASSISTANT/SYSTEM/TOOL; thêm suggestions JSON array.
    AIConversation bổ sung title; BookingDraft lưu preview và explicit confirmation.
15. **User/Staff**: User thêm role/version; Staff thêm experienceYears/completionRate/satisfactionRate/isOnline.
    averageRating/totalReviews và Customer.totalBookings là cache/read model, cần đồng bộ bằng API/event.
    username/fullName/email nằm ở users; subclasses không lưu lặp các trường này.

## Quan hệ và multiplicity cần sửa

- User → Customer/Staff/Admin dùng chung ID theo kiểu kế thừa JOINED; role ở User quyết định subtype.
  Service phải kiểm tra role đúng khi tạo profile; hiện không có API tạo profile.
- Staff → StaffArea: 1 → 0..*, không phải tối đa một khu vực.
- Booking → StaffIncome: 1 → 0..*, do booking có thể cần nhiều Staff;
  mỗi cặp bookingId/staffId chỉ có một earning.
- Booking → Review: 1 → 0..*, mỗi Staff trong booking có tối đa một review.
- Booking → Conversation: 1 → 0..*, mỗi Staff có hội thoại riêng với Customer trong booking.
- Booking → Payment: 1 → 0..*, vì có thể có nhiều payment attempt.
- Payment → Refund: 1 → 0..*, tổng các refund đang giữ/xử lý/đã hoàn không vượt amount đã thu.
- Promotion trong Catalog và Booking là liên kết logic qua promotionId/code + snapshot; không có FK xuyên database.
- Staff → StaffServiceCapability liên kết Service ở Catalog bằng serviceId, không query bảng Catalog.
- StaffRestriction thuộc Booking; Rewards/Penalties/Income/Wallet/Withdrawal thuộc Finance.
- Notification tạm thuộc Identity theo userId; tách Notification Service sau phải có migration/handoff dữ liệu.

Trong Class Diagram vẫn vẽ được association logic xuyên service. Ghi stereotype/service owner
hoặc chia package Identity/Catalog/Booking/Finance/AI để phân biệt association với FK vật lý.
DBeaver ERD từng database chỉ hiện FK nội bộ; không có đường nối xuyên database là đúng thiết kế.

## Enum và điểm chưa triển khai

- Booking status dùng STAFF_ASSIGNED; alias ASSIGNED trong Mobile cần map về STAFF_ASSIGNED.
  AssignmentStatus vẫn dùng ASSIGNED, đó là trạng thái của assignment, không phải booking.
- Payment thêm PARTIALLY_REFUNDED; Withdrawal/Refund/Quote/Draft có vòng đời riêng.
- Guard SQL đã có cho slot overlap, một default address, rating 1..5, refund total,
  immutable ledger và withdrawal thuộc đúng Staff.
- API còn phải kiểm tra Customer/Staff/service ID tồn tại, role, capability approval, trạng thái booking hợp lệ,
  quote/package cùng service, đủ tiền, giữ tiền/ghi ledger/cập nhật balance cùng transaction,
  gửi outbox, webhook signature thật và quyền truy cập conversation.
- Thông tin tài khoản ngân hàng/CCCD cần bảo vệ khi triển khai API/log/production;
  seed hiện chỉ có DEMO-ID/DEMO-ACCOUNT, không chứa CCCD hay tài khoản thật.
- Các account demo INACTIVE với passwordHash !DEMO_LOGIN_DISABLED!, không có mật khẩu đăng nhập.
  Chưa có seed role/token thật hay gọi AI/thanh toán bên ngoài.

## Migration và seed

V1/V2 giữ nguyên; V3 đã áp dụng để chuyển ID sang Long/BIGINT. Thay đổi tiếp theo bắt đầu bằng V4.
V3 chỉ tự ánh xạ các UUID thuộc bộ demo đã tạo. Nếu database khác có UUID ngoài bộ demo,
V3 dừng transaction để yêu cầu mapping rõ ràng; không xóa hay đoán ID của dữ liệu đó.
hold_token vẫn là token ngẫu nhiên UUID, không phải ID bản ghi hay trường cần đưa vào sơ đồ domain.
Seed là repeatable R__demo_data.sql trong db/dev-seed, chỉ chạy khi bật profile dev-seed.
INSERT ... ON CONFLICT DO NOTHING giữ nguyên dòng đã có, không reset hay ghi đè dữ liệu hiện tại.
Seed đồng bộ sequence sau khi thêm ID explicit để bản ghi mới tự tăng không bị trùng ID.
Các bảng refresh_tokens/idempotency_records/outbox_events được để trống đúng ý nghĩa.
