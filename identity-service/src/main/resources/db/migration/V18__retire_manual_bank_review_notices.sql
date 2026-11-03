-- Bank ownership is verified by the provider; manual review is no longer admin work.
-- Keep the audit history, and do not touch withdrawal or payout notices.
UPDATE admin_inbox_entries
SET active = FALSE, read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
WHERE source = 'finance' AND source_key LIKE 'bank:%';
