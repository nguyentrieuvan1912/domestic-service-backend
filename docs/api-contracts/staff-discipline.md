# Staff discipline, forgotten jobs and customer complaints

Added 2026-11-03. Each service owns its database; authenticated internal HTTP carries durable intents. No cross-database SQL.

## Finance

ADMIN bearer:
- GET/POST `/api/v1/finance/admin/discipline`: full audit / create direct REWARD, PENALTY, WARNING, WARN_THEN_PENALTY. Body staffId, kind, violationCode, amount, reason, requestKey. Same key/content is idempotent; different content conflicts.
- GET/PUT `/api/v1/finance/admin/discipline/policy`: noShowEnabled, graceMinutes (0..120), penaltyAmount, completionBonus (VND integers 0..1e9). History GET `/policy/history`. Actual response uses database snake_case fields.
- STAFF GET `/api/v1/finance/staff/discipline`: only own records. ADMIN can also read.
- Initial penalty 5000 VND, grace 0, no-show enabled. Completion bonus 0 until configured; no invented reward amount. WARN_THEN_PENALTY counts per staff/violation_code: first two WARNING, third onward debit. Explicit WARNING always records warning. Explicit PENALTY immediately debits when funded.
- APPLIED includes immutable ledger_id and adjustment_id; PENDING_FUNDS is retried every 15 seconds. Never overdraft. Staff advisory lock serializes counters; wallet row lock and transaction serialize financial effects.
- Internal `X-Service-Key`: GET `/internal/discipline/policy`; POST `/internal/discipline/no-show` (bookingId, assignmentId, staffId, evidence); POST `/internal/discipline/refund` (bookingId, customerId). Caller must hold committed Booking fault evidence. Refund uses only actual verified non-cash payments, credits outstanding amount to customer wallet, refuses conflicting pending refunds and records processed refund receipts; cash/unpaid amount is 0. Idempotent across reconciliation with existing customer wallet refund worker.
- Completed Booking snapshots already verified/settled by AutomaticJobSettlement produce one completion bonus per assignment, only for bookings starting from policy effective_from. Reward is atomic with settlement and ledger.

## Booking

- Staff execution changes persist immutable staff_execution_events with booking, assignment, staff, status, server occurred_at, source and note. EN_ROUTE=depart, IN_PROGRESS=arrived/met customer and begun service, COMPLETED=completed with existing evidence requirements. GET `/bookings/{id}/history` now returns merged booking + per-assignment events, id is an opaque string (`booking-...` / `execution-...`), optional assignmentId/staffId/staffName. Historical timestamps are not fabricated or backfilled.
- Every 10 seconds, confirmed, not-yet-started jobs past starts_at+grace are locked and rechecked. Missing departure is execution_status ACCEPTED on ACCEPTED assignment. Cancellation and evidence/remediation intent commit together. Only missing staff receive violation. Jobs already in progress or staff already EN_ROUTE are not cancelled by this rule. Applies starts_at >= Finance policy effective_from, not retroactively to past jobs.
- Departure endpoint independently checks deadline, so staff cannot avoid cancellation by pressing depart after cutoff before scan. Finance outage produces explicit retryable service error.
- forgotten_job_remediations keeps finance/voucher/refund receipts and last_error/checked_at. Retry preserves successes; lost HTTP acknowledgement cannot duplicate money/vouchers. ADMIN GET `/admin/forgotten-jobs` for reconciliation.
- Customer POST `/bookings/{id}/incidents`: reason, requestKey, optional staffId. Ownership checked; staff must belong to assignment; one request cannot be replayed for different content. GET same path returns only caller reports (ADMIN sees all). Existing Staff incident API unchanged. Customer complaint alone never automatically fines staff.

## Catalog

- Internal `X-Service-Key` POST `/internal/recovery-vouchers`: bookingId/customerId; one personal 5% voucher per faulty booking, single use, 30 days, assigned customer only.
- CUSTOMER GET `/customer/vouchers` returns own coupons. Personal coupons excluded from public promotions. Price quote enforces owner; existing consume logic locks quote and atomically enforces usage count.

## Clients

Web `/admin/discipline`: create commands, history/filter/details, editable policy + audit, forgotten-job processing receipts. Existing booking drawer shows staff execution timeline; incidents include actual staff names and report time.
Mobile customer booking detail includes complaint form/history and issued coupon. Staff wallet includes own rewards/penalties/warnings. Existing wallet and booking flows preserved. No client-supplied timestamps or arbitrary monetary success statuses are trusted.
