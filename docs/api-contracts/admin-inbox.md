# Admin inbox and work queues

Identity owns `admin_inbox_entries` (V16). Each entry belongs to an authenticated ADMIN, with a local FK to users. Read status is persisted independently for each administrator. No cross-database SQL or JPA links.

## Work sources

- Identity: candidates without a provisioned staff account (each recruitment stage), RECEIVED/IN_PROGRESS support, PENDING community posts, OPEN comment reports.
- Booking `GET /api/v1/booking/admin/work-items`: open incidents, PENDING service capabilities, published low ratings (1–3), PENDING/MATCHING/NO_STAFF_FOUND/ABSENT bookings, COMPLETED bookings awaiting financial reconciliation.
- Finance `GET /api/v1/finance/admin/work-items`: PENDING/unaudited bank verification, PENDING/APPROVED/PROCESSING withdrawals or payout REVIEW, pending/approved/processing/failed refunds, PENDING rewards, PENDING/APPEALED penalties, REVIEW topups or CREATING older than two minutes. Includes settledBookingIds from PAID invoices to exclude completed reconciled bookings.

The work endpoints authenticate ADMIN and query only their own databases. They return compact items: key, state, section, target, title, summary. They never approve, transfer, settle, verify, or resolve anything. The directory is not required to load these queues.

## Inbox endpoints

All routes below start `/api/v1/identity/admin/inbox` and require an ADMIN bearer session.

- POST `/sync` (no body): synchronizes current pending work from all owning services and materializes notifications for this admin. Same task/state retains its read state; next action or a reopened task creates a new unread entry and retains the previous entry as inactive history. Minimum eight seconds between refreshes per admin; concurrent refreshes serialize. PostgreSQL advisory locks and the unique active-task index prevent duplicate materialization across instances.
- GET `?filter=all|unread|pending&before={id}`: newest-first cursor pagination (50 items); filter is applied before pagination.
- PATCH `/{id}/read`: marks only this admin's entry read (404 for another owner).
- PATCH `/read-all` with `{throughId}`: marks this admin's notifications with id <= that observed watermark; a concurrent new arrival remains unread.

Response: items (id,sourceKey,section,target,title,summary,active,readAt,createdAt), unread, totalPending, pending (counts by section), pendingTargets (counts by specific destination), hasMore, nextBefore, unavailableSources, syncedAt.

`unread` is independent of pending work. Reading does not resolve work; pending badges disappear only when the owning workflow exits its actionable state. If a source fails, retain its last successfully synchronized active queue and report it unavailable; do not pretend the count is zero. Completed bookings cannot be newly resolved in the inbox unless both Booking and Finance responses are available.

## Web refresh behavior

Inbox sync every ten seconds while the tab is visible/online; data resources every fifteen seconds (heavy staff overview every thirty). Refresh also on online, focus, visibility restoration and successful Admin writes. No full-page reload or remount of editors; background errors preserve existing data. In-flight resource requests do not overlap, and obsolete session responses are discarded. The inbox opens once per login session if unread entries exist; new arrivals update the bell and pending badges. The session-storage reminder flag controls display only, never read state.

A notification opens the related Admin route/section, and successful read marking is required before navigation. There are no notifications or counts fabricated by the browser.
