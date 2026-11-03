# Conversation trash after booking completion

Booking owns the archive deadline. A whole booking/staff conversation moves out of the main inbox 24 hours after bookings.completed_at, only when status is COMPLETED. Last-message and conversation-creation times do not start or extend the deadline. Active, cancelled and completed-without-timestamp bookings are not guessed to be expired.

The 60-second sweep persists customer_trashed_at; customer inbox reads and deletion also reconcile the boundary immediately. Staff inbox reads calculate the same deadline directly from the committed booking completion. Migration V16 corrects earlier idle-message trash flags. Reconciliation also clears former flags on active/recently completed threads without changing customer deletion watermarks.

GET /api/v1/booking/conversations returns authorized conversations, including trash:

- trashedAt: nullable ISO timestamp scoped to the requesting participant. Both customer and staff clients exclude these conversations and unread counts from the main inbox, and display them in /chat/trash.
- customerTrashedAt: retained for compatibility with customer clients.
- customerDeletedThroughId: the authenticated customer's permanent deletion watermark, zero for staff. Clients must discard cached customer messages through this ID.

DELETE /api/v1/booking/conversations/{id}/trash remains customer-only. It accepts only an owned conversation already in trash, returns 204 after persistence and locks the conversation against concurrent mutation. Errors: 401 unauthenticated, 403 wrong role, 404 not owned, 409 not in trash. Staff can read their archived conversations; no staff permanent-deletion action is added.

Deletion records a watermark and timestamp, preserving staff history, messages and immutable evidence photos for complaint review. It does not destroy database evidence or restore previously deleted customer history. Completed conversations remain read-only, including inside trash. Empty completed conversations follow the same completion deadline.

Mobile and Expo Web share the main-inbox filter, unread-badge filter and trash screen. The separate landing/admin website has no customer messaging UI; admin order evidence remains available.
