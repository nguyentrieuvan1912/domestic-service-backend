-- Repair the former last-message deadline without restoring customer-deleted messages.
UPDATE conversations c SET customer_trashed_at=
 CASE WHEN b.status='COMPLETED' AND b.completed_at<=now()-interval '24 hours'
      THEN b.completed_at+interval '24 hours' ELSE NULL END
FROM bookings b
WHERE b.id=c.booking_id AND c.customer_deleted_at IS NULL;
CREATE INDEX ix_booking_chat_archive_completion ON bookings(completed_at) WHERE status='COMPLETED';
