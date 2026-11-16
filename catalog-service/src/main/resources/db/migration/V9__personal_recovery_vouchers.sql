ALTER TABLE promotions ADD COLUMN owner_customer_id BIGINT;
ALTER TABLE promotions ADD COLUMN recovery_booking_id BIGINT UNIQUE;
CREATE INDEX ix_personal_vouchers ON promotions(owner_customer_id);
