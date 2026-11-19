ALTER TABLE customer_topups DROP CONSTRAINT customer_topups_booking_id_key;
ALTER TABLE customer_topups ADD COLUMN retired_at TIMESTAMPTZ;
CREATE UNIQUE INDEX ux_customer_topups_active_booking ON customer_topups(booking_id)
    WHERE booking_id IS NOT NULL AND retired_at IS NULL;
CREATE INDEX ix_customer_topups_booking_history ON customer_topups(booking_id)
    WHERE booking_id IS NOT NULL;
