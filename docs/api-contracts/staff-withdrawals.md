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
# Manual TPBank disbursement with SePay

When `SEPAY_ENABLED=true`, `/withdrawal-capabilities` returns `mode=SEPAY_MANUAL` and readiness for this channel. payOS collections are unchanged. New payOS dispatches are blocked in manual mode; existing payOS intents remain queryable through their own provider.

- ADMIN `POST /api/v1/finance/admin/withdrawals/{id}/manual-transfer`: claim an approved withdrawal for the authenticated admin; durably lock PROCESSING before returning VietQR payload. Repeated calls reuse the same intent. Other admins cannot claim or confirm it.
- ADMIN `GET .../{id}/manual-transfer`: recipient snapshot, transfer content, amount, source last4, status/message, audit history, canTransfer/canConfirm; QR only while eligible and not yet confirmed/observed.
- ADMIN `POST .../{id}/manual-transfer/confirm`: `{recipientChecked:true,bankReference,recipientNote}`. Durable admin attestation is required; it does not independently mark PAID. Changes before settlement are append-only audited; PAID proof is immutable.
- Existing ADMIN/own STAFF `POST .../{id}/refresh` dispatches by intent provider.
- Public `POST /api/v1/finance/webhooks/sepay`: JSON raw bytes with `X-SePay-Timestamp` and `X-SePay-Signature: sha256=...`; HMAC SHA256 over `timestamp.raw_body`, 5-minute time window. Success body `{"success":true}`. Signed sample id=0 does not create financial records. Real events are append-only/deduplicated; webhook never settles a wallet.

Authenticated SePay Live API list/detail lookups verify unique bank transaction, source account/UUID/TPBank, OUT amount, zero IN, exact whole transfer reference, time, and bank reference matching admin attestation. Scheduler performs lookup only, never initiates money transfers. Ambiguity/provider failure retains the hold. SePay does not guarantee recipient account fields, so beneficiary verification remains a recorded admin responsibility.

V7 adds provider/source/manual proof fields, unique transaction/reference and immutable webhook events; V8 adds immutable confirmation audit history. Staff wallet/admin withdrawal DTOs include payoutProvider/payoutStatus/payoutMessage. Source configuration and manual setup commands are documented in the private `SEPAY_SETUP.md` runbook; no provider secrets enter frontend builds.

`SEPAY_TKP` is optional for reconciliation against the main account. With no TKP, transfer content is the unique CMR reference alone; configure webhook for all transaction directions on the selected TPBank main account without a VA-only filter. Incoming events never settle a withdrawal. API OUT lookup remains authoritative even if webhook configuration/delivery is unavailable.


### Automatic bank reference (SePay manual transfer)

The manual-transfer detail response adds `bankProofVerified` and `observedReference`. These are derived only from the stored result of an authenticated, exact-one SePay API lookup that passed the source account UUID/number/TPBank, OUT direction, exact amount, unique CMR token and timestamp checks. Ambiguous or mismatched evidence does not expose a verified reference.

`POST /admin/withdrawals/{id}/manual-transfer/confirm` accepts `useObservedReference: true` with `recipientChecked: true`. The server performs a fresh provider lookup and selects `reference_number` under the wallet/withdrawal/payout locks; any client-provided bankReference is ignored in this mode. An optional recipientNote may be supplied (8–2000 chars); otherwise the server records an audit note from the fixed withdrawal recipient snapshot and the explicit Admin attestation. The previous manual reference/note body remains supported.

This removes manual transcription of the TPBank receipt reference. It does not independently verify the recipient account holder: Admin must still compare bank/account/holder/amount on the bank app and explicitly attest success. Provider outage, absent proof, ambiguity or mismatch retains the reserved balance. Existing exactly-once ledger and transaction uniqueness constraints remain authoritative. Neither viewing the dialog nor automatic polling submits an attestation or initiates a bank transfer.

API reference: https://developer.sepay.vn/vi/sepay-api/v2/giao-dich/danh-sach (`reference_number`, OUT filtering, content search).


### Bank-account review audit

Finance V9 adds append-only `bank_account_reviews` with its own bank-account FK. A successful authenticated Admin decision atomically records the actor ID/name snapshot, previous/new status, bank BIN/account/holder snapshot, verification note and review timestamp, then updates current verification metadata. The actor is obtained from Identity authentication, never a client-supplied ID. Identical retries by the same actor do not replace the timestamp or duplicate the audit. Later decisions retain prior entries; database triggers reject update/delete of audit records.

Existing verified/inactive account metadata is imported once during V9 only where the original Admin ID and timestamp were already recorded. Such entries are marked legacy=true; prior status and Admin name are not invented.

`GET /api/v1/finance/admin/bank-accounts` adds verifiedBy, verifiedByName, verificationNote alongside verifiedAt. `GET /api/v1/finance/admin/bank-accounts/{id}/history` requires ADMIN and returns newest-first audit entries: id,adminId,adminName,oldStatus,newStatus,bankCode,accountNumber,accountHolder,verificationNote,legacy,reviewedAt. No cross-database user foreign key is introduced.


### Staff cancellation before approval

`POST /api/v1/finance/staff/me/withdrawals/{id}/cancel` requires the owning STAFF session. Only PENDING requests without any payout intent can be cancelled. APPROVED/PROCESSING/paid/rejected/failed requests return409; another owner's ID returns404, ADMIN cannot invoke this staff action.

Finance locks wallet then withdrawal, matching approval/payout lock order. The transaction moves the exact request amount from RESERVED to AVAILABLE, adds the two immutable RELEASE ledger entries with the existing withdrawal release idempotency keys, records CANCELLED/cancelledBy/cancelledAt/cancellationReason (V10), and creates one staff outbox notification. Replaying an already-cancelled request succeeds without repeating credit or notification. Insufficient reserves or a ledger failure rolls back the entire operation. Total earned and withdrawn amounts do not increase.

Response: id,status=CANCELLED,amount. Wallet/Admin withdrawal records expose cancellationReason,cancelledAt,cancelledBy. The mobile reserved-funds card lists a cancel action only for PENDING requests and refreshes the real wallet after the server confirms cancellation; no optimistic balance arithmetic. Admin's inbox queue excludes cancelled work on the next sync, while the cancellation remains visible in history.
