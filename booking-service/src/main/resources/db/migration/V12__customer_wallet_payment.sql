ALTER TABLE bookings DROP CONSTRAINT bookings_payment_method_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_payment_method_check CHECK(payment_method IN ('BANK_TRANSFER_QR','CASH','WALLET'));
ALTER TABLE bookings DROP CONSTRAINT bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check CHECK(status IN ('PENDING','AWAITING_PAYMENT','MATCHING','CONFIRMED','STAFF_ASSIGNED','ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED','REFUNDING','REFUNDED','NO_STAFF_FOUND','ABSENT','REJECTED'));
CREATE FUNCTION guard_booking_payment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status='ACCEPTED' AND EXISTS(SELECT 1 FROM bookings WHERE id=NEW.booking_id AND payment_method<>'CASH' AND payment_status<>'PAID') THEN
  RAISE EXCEPTION 'Booking payment has not been verified' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER booking_payment_gate BEFORE INSERT OR UPDATE OF status ON booking_assignments
 FOR EACH ROW EXECUTE FUNCTION guard_booking_payment();
