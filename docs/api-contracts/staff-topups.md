# Staff topups — payOS, phase 1

Finance owns all new tables. Staff IDs reference Identity only by BIGINT, no cross-database joins.
Customer booking QR and payouts are separate later phases; existing cash booking flow is unchanged.

All endpoints below require Bearer authentication except the signed webhook and informational return page.

| Method | Finance-relative route | Access / behavior |
|---|---|---|
| GET | /payment-capabilities | STAFF/CUSTOMER/ADMIN; configuration flags, never keys; topupEnabled, payoutEnabled=false |
| GET | /staff/me/topups | STAFF; latest 100 owned requests |
| POST | /staff/me/topups | STAFF; `{amount,requestKey}`; integer VND 2000–1000000; stable requestKey reuses one intent |
| POST | /staff/me/topups/{id}/refresh | STAFF; only owned request; verifies with payOS |
| GET | /admin/topups | ADMIN; latest 100 requests |
| POST | /admin/topups/{id}/refresh | ADMIN; reconciliation only, no manual paid switch |
| POST | /webhooks/payos | Public HMAC SHA256, persist known event, re-fetch provider proof before credit |
| GET | /payments/return | Public informational text only; never marks paid/cancelled |

Response topup: id, staffId, amount, requestKey, status, description, checkoutUrl,
accountNumber, accountName, bankBin, amountReceived, createdAt, expiresAt, verifiedAt, paidAt.
Unconfigured provider: 503 on creation; no fake QR or wallet credit. Capabilities remains readable.
403 for wrong role; 404 for another staff's request; 409 for request key reused with a different amount.

States: CREATING, PENDING, PAID, CANCELLED, EXPIRED, REVIEW.
Only signed provider PAID proof with matching order, link, exact paid amount, zero remaining,
and a previously verified receiving account credits the wallet. Partial/excess or unverified receiving account goes to REVIEW.
Lost creation responses do not produce a second intent. They remain under reconciliation; no guessed checkout URL or unverified credit.
Provider setup samples/unknown order codes are acknowledged after signature verification but cannot create a topup/wallet.

V5 adds staff_topups, topup_provider_events, topup_money_entries; wallet transaction type TOP_UP.
Successful topup atomically credits AVAILABLE and records bank debit/staff liability credit;
total_earned is unchanged. Finance balance.reserved remains the existing held withdrawal total.
Wallet and money entries are append-only. No withdrawal/booking settlement policy changes here.

Finance-only Docker environment variables: PAYOS_ENABLED, PAYOS_CLIENT_ID, PAYOS_API_KEY,
PAYOS_CHECKSUM_KEY, PAYOS_ACCOUNT_NUMBER, PAYOS_RETURN_URL, PAYOS_CANCEL_URL.
Use ignored .env, never frontend variables. Configuration readiness is not a successful bank connection test.
