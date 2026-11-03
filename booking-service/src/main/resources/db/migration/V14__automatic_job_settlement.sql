ALTER TABLE bookings ADD COLUMN finance_sync_required BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE bookings ADD COLUMN finance_checked_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE bookings ADD COLUMN finance_sync_error TEXT;
ALTER TABLE booking_assignments ADD COLUMN cash_received_at TIMESTAMPTZ;
CREATE INDEX ix_booking_finance_sync ON bookings(finance_checked_at) WHERE finance_sync_required OR payment_method='CASH';
ALTER TABLE booking_assignments ADD COLUMN cash_collection_amount BIGINT CHECK(cash_collection_amount > 0);
CREATE FUNCTION require_cash_collection_confirmation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.execution_status='COMPLETED' AND OLD.execution_status IS DISTINCT FROM 'COMPLETED'
 AND EXISTS(SELECT 1 FROM bookings WHERE id=NEW.booking_id AND payment_method='CASH')
 AND NEW.cash_received_at IS NULL THEN
  RAISE EXCEPTION 'Cash completion requires staff collection confirmation' USING ERRCODE='23514',CONSTRAINT='cash_collection_confirmation';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER cash_collection_confirmation BEFORE UPDATE OF execution_status ON booking_assignments
 FOR EACH ROW EXECUTE FUNCTION require_cash_collection_confirmation();
