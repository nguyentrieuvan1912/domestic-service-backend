# Staff withdrawal payouts

Finance V6 adds withdrawal_payouts and append-only payout_money_entries, and manual bank-verification audit fields.
Existing request/approval endpoints remain. An approved request never initiates a transfer just because configuration is later enabled.

## Configuration

Finance only: PAYOS_PAYOUT_ENABLED (default false), PAYOS_PAYOUT_CLIENT_ID, PAYOS_PAYOUT_API_KEY,
PAYOS_PAYOUT_CHECKSUM_KEY, PAYOS_PAYOUT_SOURCE_ACCOUNT, PAYOS_PAYOUT_SOURCE_NAME,
PAYOS_PAYOUT_BANK_BINS (comma-separated destination BINs confirmed supported for this channel),
PAYOS_PAYOUT_MINIMUM (default 50000), PAYOS_PAYOUT_MAXIMUM (default 1000000).
The collection channel keys and per-QR virtual receiving accounts are unrelated to this source account.
Credentials stay backend-only. Configured is not proof of connectivity.

## API additions

All routes are under /api/v1/finance with Bearer authentication.

| Method | Route | Behavior |
|---|---|---|
| GET | /withdrawal-capabilities | STAFF/ADMIN: configured, minimum, maximum, requiresAdminApproval=true, automaticNameLookup=false, message |
| GET | /payout-banks | STAFF/ADMIN: real VietQR directory filtered by explicitly allowed channel BINs and transferSupported=1; no allowlist means empty list |
| GET | /admin/payouts/connection | ADMIN only: signed source proof must match configured source number/name and VND; returns verified, balance, accountName, accountLast4 |
| POST | /admin/withdrawals/{id}/payout | ADMIN, body {confirmed:true}, approved withdrawal, audited verified account matching immutable snapshot; validates source and creates a durable single intent before provider I/O |
| POST | /admin/withdrawals/{id}/refresh | ADMIN or owning STAFF; lookup and reconciliation only |
| POST | /staff/me/withdrawals/{id}/refresh | ADMIN or owning STAFF; lookup and reconciliation only |

POST /staff/me/bank-accounts now accepts only a configured supported bank BIN in bankCode (six digits),
accountNumber and manually entered accountHolder. Saving does not verify the account/name.
PATCH /admin/bank-accounts/{id} with VERIFIED also requires verificationNote (8–2000 characters), recording the
admin and verification time. Legacy verified records need rechecking before payout; admin queue includes them.
Neither a profile name nor a successful bank-form save counts as a bank lookup.

GET admin/staff withdrawals adds payoutStatus and payoutMessage. Admin rows still contain the saved bankAccount JSON snapshot.

## Financial transitions

Existing withdrawal creation atomically moves AVAILABLE to RESERVED and remains PENDING until admin approval.
Only an explicit confirmed admin send can create a payout intent, then withdrawal becomes PROCESSING.
Unique withdrawal_id/reference_id/idempotency_key prevent double submission, including concurrent clicks.
The job only reconciles already persisted attempts every 30 seconds; it never sends approved requests.
Transport failures retain RESERVED, mark the intent REVIEW, and only lookup the same reference on retries.
A missing provider result never proves failure and never releases money.

Signed provider results must match the reference, payout ID (once known), exactly one transaction, amount,
destination BIN/account, and normalized audited account holder name before successful settlement.
Only SUCCEEDED transaction plus COMPLETED/SUCCEEDED approval settles; RESERVED is debited once, withdrawn_amount
increments once, and balanced staff-payable/bank-disbursement entries are appended. Creating/approving a provider
order is not success. Mismatches/unknown/reversed/cancelled results remain REVIEW. A matched FAILED transaction
with FAILED/COMPLETED approval releases the hold once and marks withdrawal FAILED.

Payout signing follows the official Node SDK's URI-encoded, recursively key-sorted canonical data, not the
collection-link signature scheme. Source/create/get/list results require the x-signature response header.
Provider SDK/API references: https://github.com/payOSHQ/payos-lib-node and https://payos.vn/docs/api/.

The app lists available banks, accepts the real account holder input, refreshes wallet/bank statuses while
foreground/focused, and sends actual request IDs. The admin has separate approve, send-real-money and reconcile
actions. Provider/service fees use the company channel; no unverified fee amount or instant/free-transfer promise
is displayed. Reversals after an already settled payment require operator reconciliation; no automatic recredit.
