# Đối chiếu sơ đồ lớp với Entity và Repository

Đối chiếu với schema PostgreSQL đã áp dụng. Flyway vẫn quản lý schema.

Cấu trúc: `controller` → `service` → `repository`; lớp JPA ở `entity`, request/response ở `dto`.

Khóa ngoại nội bộ dùng quan hệ JPA tải lười. Quan hệ khác service giữ ID Long. User/Admin/Customer/Staff dùng kế thừa JOINED với khóa chính chung. Không bổ sung cascade xóa.

## identity-service

| Lớp / Entity | Bảng | Repository |
|---|---|---|
| [AccountSession](../identity-service/src/main/java/com/domesticservice/identity/entity/AccountSession.java) | `account_sessions` | [AccountSessionRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/AccountSessionRepository.java) |
| [Address](../identity-service/src/main/java/com/domesticservice/identity/entity/Address.java) | `addresses` | [AddressRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/AddressRepository.java) |
| [AdministrativeUnit](../identity-service/src/main/java/com/domesticservice/identity/entity/AdministrativeUnit.java) | `administrative_units` | [AdministrativeUnitRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/AdministrativeUnitRepository.java) |
| [Admin](../identity-service/src/main/java/com/domesticservice/identity/entity/Admin.java) | `admins` | [AdminRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/AdminRepository.java) |
| [CommunityComment](../identity-service/src/main/java/com/domesticservice/identity/entity/CommunityComment.java) | `community_comments` | [CommunityCommentRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/CommunityCommentRepository.java) |
| [CommunityLike](../identity-service/src/main/java/com/domesticservice/identity/entity/CommunityLike.java) | `community_likes` | [CommunityLikeRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/CommunityLikeRepository.java) |
| [CommunityPost](../identity-service/src/main/java/com/domesticservice/identity/entity/CommunityPost.java) | `community_posts` | [CommunityPostRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/CommunityPostRepository.java) |
| [CustomerFavorite](../identity-service/src/main/java/com/domesticservice/identity/entity/CustomerFavorite.java) | `customer_favorites` | [CustomerFavoriteRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/CustomerFavoriteRepository.java) |
| [Customer](../identity-service/src/main/java/com/domesticservice/identity/entity/Customer.java) | `customers` | [CustomerRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/CustomerRepository.java) |
| [Notification](../identity-service/src/main/java/com/domesticservice/identity/entity/Notification.java) | `notifications` | [NotificationRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/NotificationRepository.java) |
| [RefreshToken](../identity-service/src/main/java/com/domesticservice/identity/entity/RefreshToken.java) | `refresh_tokens` | [RefreshTokenRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/RefreshTokenRepository.java) |
| [RegistrationVerification](../identity-service/src/main/java/com/domesticservice/identity/entity/RegistrationVerification.java) | `registration_verifications` | [RegistrationVerificationRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/RegistrationVerificationRepository.java) |
| [StaffCandidate](../identity-service/src/main/java/com/domesticservice/identity/entity/StaffCandidate.java) | `staff_candidates` | [StaffCandidateRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/StaffCandidateRepository.java) |
| [Staff](../identity-service/src/main/java/com/domesticservice/identity/entity/Staff.java) | `staffs` | [StaffRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/StaffRepository.java) |
| [SupportRequest](../identity-service/src/main/java/com/domesticservice/identity/entity/SupportRequest.java) | `support_requests` | [SupportRequestRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/SupportRequestRepository.java) |
| [User](../identity-service/src/main/java/com/domesticservice/identity/entity/User.java) | `users` | [UserRepository](../identity-service/src/main/java/com/domesticservice/identity/repository/UserRepository.java) |

ID tham chiếu ngoài service hoặc đa hình, không tạo quan hệ JPA xuyên database:

- `customer_favorites`: `service_id`
- `notifications`: `reference_id`
- `registration_verifications`: `challenge_id`

## catalog-service

| Lớp / Entity | Bảng | Repository |
|---|---|---|
| [AddOn](../catalog-service/src/main/java/com/domesticservice/catalog/entity/AddOn.java) | `add_ons` | [AddOnRepository](../catalog-service/src/main/java/com/domesticservice/catalog/repository/AddOnRepository.java) |
| [PriceQuote](../catalog-service/src/main/java/com/domesticservice/catalog/entity/PriceQuote.java) | `price_quotes` | [PriceQuoteRepository](../catalog-service/src/main/java/com/domesticservice/catalog/repository/PriceQuoteRepository.java) |
| [PromotionService](../catalog-service/src/main/java/com/domesticservice/catalog/entity/PromotionService.java) | `promotion_services` | [PromotionServiceRepository](../catalog-service/src/main/java/com/domesticservice/catalog/repository/PromotionServiceRepository.java) |
| [Promotion](../catalog-service/src/main/java/com/domesticservice/catalog/entity/Promotion.java) | `promotions` | [PromotionRepository](../catalog-service/src/main/java/com/domesticservice/catalog/repository/PromotionRepository.java) |
| [ServiceCategory](../catalog-service/src/main/java/com/domesticservice/catalog/entity/ServiceCategory.java) | `service_categories` | [ServiceCategoryRepository](../catalog-service/src/main/java/com/domesticservice/catalog/repository/ServiceCategoryRepository.java) |
| [ServicePackage](../catalog-service/src/main/java/com/domesticservice/catalog/entity/ServicePackage.java) | `service_packages` | [ServicePackageRepository](../catalog-service/src/main/java/com/domesticservice/catalog/repository/ServicePackageRepository.java) |
| [ServiceRequirement](../catalog-service/src/main/java/com/domesticservice/catalog/entity/ServiceRequirement.java) | `service_requirements` | [ServiceRequirementRepository](../catalog-service/src/main/java/com/domesticservice/catalog/repository/ServiceRequirementRepository.java) |
| [Service](../catalog-service/src/main/java/com/domesticservice/catalog/entity/Service.java) | `services` | [ServiceRepository](../catalog-service/src/main/java/com/domesticservice/catalog/repository/ServiceRepository.java) |

ID tham chiếu ngoài service hoặc đa hình, không tạo quan hệ JPA xuyên database:

- `price_quotes`: `customer_id`, `consumed_booking_id`

## booking-service

| Lớp / Entity | Bảng | Repository |
|---|---|---|
| [BookingAddOn](../booking-service/src/main/java/com/domesticservice/booking/entity/BookingAddOn.java) | `booking_add_ons` | [BookingAddOnRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/BookingAddOnRepository.java) |
| [BookingAssignment](../booking-service/src/main/java/com/domesticservice/booking/entity/BookingAssignment.java) | `booking_assignments` | [BookingAssignmentRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/BookingAssignmentRepository.java) |
| [BookingIncident](../booking-service/src/main/java/com/domesticservice/booking/entity/BookingIncident.java) | `booking_incidents` | [BookingIncidentRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/BookingIncidentRepository.java) |
| [BookingRequirementAnswer](../booking-service/src/main/java/com/domesticservice/booking/entity/BookingRequirementAnswer.java) | `booking_requirement_answers` | [BookingRequirementAnswerRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/BookingRequirementAnswerRepository.java) |
| [BookingStatusHistory](../booking-service/src/main/java/com/domesticservice/booking/entity/BookingStatusHistory.java) | `booking_status_histories` | [BookingStatusHistoryRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/BookingStatusHistoryRepository.java) |
| [Booking](../booking-service/src/main/java/com/domesticservice/booking/entity/Booking.java) | `bookings` | [BookingRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/BookingRepository.java) |
| [Conversation](../booking-service/src/main/java/com/domesticservice/booking/entity/Conversation.java) | `conversations` | [ConversationRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/ConversationRepository.java) |
| [IdempotencyRecord](../booking-service/src/main/java/com/domesticservice/booking/entity/IdempotencyRecord.java) | `idempotency_records` | [IdempotencyRecordRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/IdempotencyRecordRepository.java) |
| [Message](../booking-service/src/main/java/com/domesticservice/booking/entity/Message.java) | `messages` | [MessageRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/MessageRepository.java) |
| [OutboxEvent](../booking-service/src/main/java/com/domesticservice/booking/entity/OutboxEvent.java) | `outbox_events` | [OutboxEventRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/OutboxEventRepository.java) |
| [Review](../booking-service/src/main/java/com/domesticservice/booking/entity/Review.java) | `reviews` | [ReviewRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/ReviewRepository.java) |
| [StaffArea](../booking-service/src/main/java/com/domesticservice/booking/entity/StaffArea.java) | `staff_areas` | [StaffAreaRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/StaffAreaRepository.java) |
| [StaffAvailability](../booking-service/src/main/java/com/domesticservice/booking/entity/StaffAvailability.java) | `staff_availabilities` | [StaffAvailabilityRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/StaffAvailabilityRepository.java) |
| [StaffReservation](../booking-service/src/main/java/com/domesticservice/booking/entity/StaffReservation.java) | `staff_reservations` | [StaffReservationRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/StaffReservationRepository.java) |
| [StaffRestriction](../booking-service/src/main/java/com/domesticservice/booking/entity/StaffRestriction.java) | `staff_restrictions` | [StaffRestrictionRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/StaffRestrictionRepository.java) |
| [StaffScheduleSettings](../booking-service/src/main/java/com/domesticservice/booking/entity/StaffScheduleSettings.java) | `staff_schedule_settings` | [StaffScheduleSettingsRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/StaffScheduleSettingsRepository.java) |
| [StaffScheduleWeek](../booking-service/src/main/java/com/domesticservice/booking/entity/StaffScheduleWeek.java) | `staff_schedule_weeks` | [StaffScheduleWeekRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/StaffScheduleWeekRepository.java) |
| [StaffServiceCapability](../booking-service/src/main/java/com/domesticservice/booking/entity/StaffServiceCapability.java) | `staff_service_capabilities` | [StaffServiceCapabilityRepository](../booking-service/src/main/java/com/domesticservice/booking/repository/StaffServiceCapabilityRepository.java) |

ID tham chiếu ngoài service hoặc đa hình, không tạo quan hệ JPA xuyên database:

- `booking_add_ons`: `add_on_id`
- `booking_assignments`: `staff_id`
- `booking_incidents`: `reporter_id`
- `booking_requirement_answers`: `requirement_id`
- `bookings`: `customer_id`, `service_id`, `package_id`, `address_id`, `quote_id`, `promotion_id`
- `conversations`: `customer_id`, `staff_id`
- `idempotency_records`: `actor_id`
- `messages`: `sender_id`
- `outbox_events`: `aggregate_id`
- `reviews`: `customer_id`, `staff_id`, `service_id`
- `staff_areas`: `staff_id`
- `staff_availabilities`: `staff_id`
- `staff_reservations`: `staff_id`
- `staff_restrictions`: `staff_id`, `service_id`
- `staff_schedule_settings`: `staff_id`
- `staff_schedule_weeks`: `staff_id`
- `staff_service_capabilities`: `staff_id`, `service_id`

## finance-service

| Lớp / Entity | Bảng | Repository |
|---|---|---|
| [FinanceSettings](../finance-service/src/main/java/com/domesticservice/finance/entity/FinanceSettings.java) | `finance_settings` | [FinanceSettingsRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/FinanceSettingsRepository.java) |
| [IdempotencyRecord](../finance-service/src/main/java/com/domesticservice/finance/entity/IdempotencyRecord.java) | `idempotency_records` | [IdempotencyRecordRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/IdempotencyRecordRepository.java) |
| [InvoiceItem](../finance-service/src/main/java/com/domesticservice/finance/entity/InvoiceItem.java) | `invoice_items` | [InvoiceItemRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/InvoiceItemRepository.java) |
| [Invoice](../finance-service/src/main/java/com/domesticservice/finance/entity/Invoice.java) | `invoices` | [InvoiceRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/InvoiceRepository.java) |
| [OrderDetail](../finance-service/src/main/java/com/domesticservice/finance/entity/OrderDetail.java) | `order_details` | [OrderDetailRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/OrderDetailRepository.java) |
| [Order](../finance-service/src/main/java/com/domesticservice/finance/entity/Order.java) | `orders` | [OrderRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/OrderRepository.java) |
| [OutboxEvent](../finance-service/src/main/java/com/domesticservice/finance/entity/OutboxEvent.java) | `outbox_events` | [OutboxEventRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/OutboxEventRepository.java) |
| [PaymentWebhookEvent](../finance-service/src/main/java/com/domesticservice/finance/entity/PaymentWebhookEvent.java) | `payment_webhook_events` | [PaymentWebhookEventRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/PaymentWebhookEventRepository.java) |
| [Payment](../finance-service/src/main/java/com/domesticservice/finance/entity/Payment.java) | `payments` | [PaymentRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/PaymentRepository.java) |
| [Penalty](../finance-service/src/main/java/com/domesticservice/finance/entity/Penalty.java) | `penalties` | [PenaltyRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/PenaltyRepository.java) |
| [Refund](../finance-service/src/main/java/com/domesticservice/finance/entity/Refund.java) | `refunds` | [RefundRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/RefundRepository.java) |
| [Reward](../finance-service/src/main/java/com/domesticservice/finance/entity/Reward.java) | `rewards` | [RewardRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/RewardRepository.java) |
| [StaffBalance](../finance-service/src/main/java/com/domesticservice/finance/entity/StaffBalance.java) | `staff_balances` | [StaffBalanceRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/StaffBalanceRepository.java) |
| [StaffBankAccount](../finance-service/src/main/java/com/domesticservice/finance/entity/StaffBankAccount.java) | `staff_bank_accounts` | [StaffBankAccountRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/StaffBankAccountRepository.java) |
| [StaffIncome](../finance-service/src/main/java/com/domesticservice/finance/entity/StaffIncome.java) | `staff_incomes` | [StaffIncomeRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/StaffIncomeRepository.java) |
| [WalletTransaction](../finance-service/src/main/java/com/domesticservice/finance/entity/WalletTransaction.java) | `wallet_transactions` | [WalletTransactionRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/WalletTransactionRepository.java) |
| [Withdrawal](../finance-service/src/main/java/com/domesticservice/finance/entity/Withdrawal.java) | `withdrawals` | [WithdrawalRepository](../finance-service/src/main/java/com/domesticservice/finance/repository/WithdrawalRepository.java) |

ID tham chiếu ngoài service hoặc đa hình, không tạo quan hệ JPA xuyên database:

- `idempotency_records`: `actor_id`
- `invoices`: `booking_id`, `customer_id`
- `order_details`: `reference_id`
- `orders`: `booking_id`
- `outbox_events`: `aggregate_id`
- `payment_webhook_events`: `provider_event_id`
- `payments`: `booking_id`, `customer_id`
- `penalties`: `staff_id`, `booking_id`
- `rewards`: `staff_id`
- `staff_balances`: `staff_id`
- `staff_bank_accounts`: `staff_id`
- `staff_incomes`: `booking_id`
- `wallet_transactions`: `reference_id`

## ai-assistant-service

| Lớp / Entity | Bảng | Repository |
|---|---|---|
| [AIConversation](../ai-assistant-service/src/main/java/com/domesticservice/ai/entity/AIConversation.java) | `ai_conversations` | [AIConversationRepository](../ai-assistant-service/src/main/java/com/domesticservice/ai/repository/AIConversationRepository.java) |
| [AIMessage](../ai-assistant-service/src/main/java/com/domesticservice/ai/entity/AIMessage.java) | `ai_messages` | [AIMessageRepository](../ai-assistant-service/src/main/java/com/domesticservice/ai/repository/AIMessageRepository.java) |
| [AIToolCall](../ai-assistant-service/src/main/java/com/domesticservice/ai/entity/AIToolCall.java) | `ai_tool_calls` | [AIToolCallRepository](../ai-assistant-service/src/main/java/com/domesticservice/ai/repository/AIToolCallRepository.java) |
| [BookingDraft](../ai-assistant-service/src/main/java/com/domesticservice/ai/entity/BookingDraft.java) | `booking_drafts` | [BookingDraftRepository](../ai-assistant-service/src/main/java/com/domesticservice/ai/repository/BookingDraftRepository.java) |
| [IdempotencyRecord](../ai-assistant-service/src/main/java/com/domesticservice/ai/entity/IdempotencyRecord.java) | `idempotency_records` | [IdempotencyRecordRepository](../ai-assistant-service/src/main/java/com/domesticservice/ai/repository/IdempotencyRecordRepository.java) |
| [KnowledgeChunk](../ai-assistant-service/src/main/java/com/domesticservice/ai/entity/KnowledgeChunk.java) | `knowledge_chunks` | [KnowledgeChunkRepository](../ai-assistant-service/src/main/java/com/domesticservice/ai/repository/KnowledgeChunkRepository.java) |
| [KnowledgeDocument](../ai-assistant-service/src/main/java/com/domesticservice/ai/entity/KnowledgeDocument.java) | `knowledge_documents` | [KnowledgeDocumentRepository](../ai-assistant-service/src/main/java/com/domesticservice/ai/repository/KnowledgeDocumentRepository.java) |

ID tham chiếu ngoài service hoặc đa hình, không tạo quan hệ JPA xuyên database:

- `ai_conversations`: `customer_id`
- `booking_drafts`: `customer_id`, `quote_id`, `booking_id`
- `idempotency_records`: `actor_id`

## notification-service

| Lớp / Entity | Bảng | Repository |
|---|---|---|
| [NotificationDelivery](../notification-service/src/main/java/com/domesticservice/notification/entity/NotificationDelivery.java) | `notification_deliveries` | [NotificationDeliveryRepository](../notification-service/src/main/java/com/domesticservice/notification/repository/NotificationDeliveryRepository.java) |
| [NotificationDevice](../notification-service/src/main/java/com/domesticservice/notification/entity/NotificationDevice.java) | `notification_devices` | [NotificationDeviceRepository](../notification-service/src/main/java/com/domesticservice/notification/repository/NotificationDeviceRepository.java) |
| [NotificationEvent](../notification-service/src/main/java/com/domesticservice/notification/entity/NotificationEvent.java) | `notification_events` | [NotificationEventRepository](../notification-service/src/main/java/com/domesticservice/notification/repository/NotificationEventRepository.java) |
| [Notification](../notification-service/src/main/java/com/domesticservice/notification/entity/Notification.java) | `notifications` | [NotificationRepository](../notification-service/src/main/java/com/domesticservice/notification/repository/NotificationRepository.java) |

ID tham chiếu ngoài service hoặc đa hình, không tạo quan hệ JPA xuyên database:

- `notification_devices`: `user_id`
- `notifications`: `user_id`, `reference_id`
