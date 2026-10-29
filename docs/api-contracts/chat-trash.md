# Customer conversation trash

Booking service owns the customer trash state. Migration V11 adds conversations.customer_trashed_at, customer_deleted_at and customer_deleted_through_id. Existing booking/conversation/message IDs and chat permissions are unchanged.

A whole order/staff conversation enters customer trash 24 hours after its most recent message (or creation for an empty conversation). A scheduled sweep runs every 60 seconds; customer inbox reads and delete operations also reconcile the expiry. Sending a new message clears the trash flag and returns the conversation to the main customer inbox. Staff history is unaffected by customer trash actions.

GET /api/v1/booking/conversations still returns both roles' authorized conversations. New fields:
- customerTrashedAt: nullable ISO timestamp. Customer clients show these threads in trash and exclude their unread counts from the main inbox badge.
- customerDeletedThroughId: message high-water mark for the authenticated customer; zero for staff. Clients must discard cached customer messages through this ID, including after a deletion on another device.

DELETE /api/v1/booking/conversations/{id}/trash permanently removes the current history from the authenticated customer's account. It only accepts a customer-owned conversation currently in trash and returns 204 after persistence. Errors: 401 unauthenticated, 403 wrong role, 404 another customer's conversation, 409 not in trash (including a new message received since the confirmation dialog opened). The server locks the conversation against concurrent send/delete.

Deletion stores a permanent high-water mark and timestamp rather than destroying the staff member's copy. Customer message reads/previews/unread counts exclude messages through that mark; staff reads retain their existing history. A future message may reopen the same conversation but cannot restore the customer's deleted history. Empty deleted conversations remain hidden until their first new message. Booking, assignment and call records are retained.

Mobile and Expo Web share the inbox trash button and /chat/trash screen. The separate landing/admin website currently has no customer messaging UI.
