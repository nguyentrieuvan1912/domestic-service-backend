# Customer wallet and prepaid bookings

Customer endpoints require the authenticated CUSTOMER bearer session. IDs are numeric. Amounts are integer VND; the server obtains booking prices from Booking, never from the client.

| Method | Finance path under `/api/v1/finance` | Response / request |
|---|---|---|
| GET | `/customer/me/wallet` | `{available,transactions:[{id,type,direction,amount,bookingId,createdAt}]}` |
| GET | `/customer/me/topups` | Up to 100 account-owned intents, including booking QR intents |
| POST | `/customer/me/topups` | `{amount,requestKey}`; 2,000–1,000,000 VND. Retry the same key after a network failure. |
| POST | `/customer/me/topups/{id}/refresh` | Reconcile the owned intent with signed payOS evidence |
| POST | `/customer/bookings/{id}/pay` | WALLET: debit the exact price once; QR: `{topup,payment}` with the persisted QR intent |
| POST | `/bookings/{id}/payment` | Existing route aliases the same payment operation |

Topup responses include `id,customerId,bookingId,amount,requestKey,status,qrCode,accountNumber,accountName,bankBin,description,amountReceived,expiresAt,paidAt,verifiedAt`. QR is exposed only for verified, pending, unexpired intents. Wallet entries are append-only. Separate customer/staff tables and provider order code ranges prevent ownership or provider collisions.

Booking create accepts exactly `CASH`, `BANK_TRANSFER_QR`, `WALLET`. Cash retains the existing flow. The other methods persist a priced `AWAITING_PAYMENT` booking; it cannot be claimed or dispatched to staff. Clients show a payment page and may resume from booking detail. QR intents use the booking's exact amount; the bank transaction must be independently confirmed by payOS. A redirect or pressing the check button is not proof of payment.

Customer topups use the configured, existing payOS channel and `/webhooks/payos`. Signed webhook events trigger a signed provider lookup. Partial receipts remain REVIEW without credit or staff dispatch; once PAID with no remaining amount, the full receipt is credited once. Booking QR credit and exact booking debit commit atomically; any excess stays in the wallet. Late payment of a canceled order is also recorded and then refunded. A missing verified receiving account remains REVIEW and is never assumed to be paid.

Finance reconciles durable charges with Booking every 5 seconds in bounded batches. Booking independently reads Finance's payment proof before moving AWAITING_PAYMENT to MATCHING/STAFF_ASSIGNED and starting the invitation deadline. It rechecks staff eligibility after payment. If there are too few eligible staff or less than five minutes before the start, it ends as NO_STAFF_FOUND. Unfilled prepaid matching orders also end five minutes before start. CANCELLED/NO_STAFF_FOUND/REJECTED triggers a full, idempotent refund to the original customer's wallet. No bank payout occurs. Refund retries survive service failures, and REFUNDED cannot regress to PAID. Expired prepaid Mode A invitations must be rebooked using wallet funds; cash invitations retain their extension behavior.

Internal endpoints require constant-time checked `X-Service-Key` matching existing `INTERNAL_SERVICE_TOKEN`; an empty key disables payment collection/activation. These are service APIs, not customer UI calls:

- Booking GET `/api/v1/booking/internal/bookings/{id}` returns the booking snapshot.
- Booking POST `/api/v1/booking/internal/bookings/{id}/reconcile-payment` re-reads Finance proof and commits a local transition.
- Finance GET `/api/v1/finance/internal/customer-payments/{id}` returns gross paid and refunded amounts.

No cross-database queries or shared JPA entities are used. Migrations: Finance V13 and Booking V12. PayOS creation and verification use the existing configured credentials, account pinning and signed HTTP client. No new provider credentials or mock runtime data are added.

Verification: `mvnw.cmd verify`; optional real PostgreSQL integration tests `CustomerWalletPostgresTests` with `TOPUP_POSTGRES_VERIFY=true` / `TOPUP_TEST_URL`, and `BookingPaymentPostgresTests` with `CUSTOMER_PAYMENT_POSTGRES_VERIFY=true` / `BOOKING_WALLET_TEST_URL`. Run these only against disposable databases (test credentials are fixed in test annotations); HTTP peers in those tests are isolated fixtures and never make real transfers.
