ALTER TABLE withdrawals ADD COLUMN cancelled_by BIGINT;
ALTER TABLE withdrawals ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE withdrawals ADD COLUMN cancellation_reason TEXT;
