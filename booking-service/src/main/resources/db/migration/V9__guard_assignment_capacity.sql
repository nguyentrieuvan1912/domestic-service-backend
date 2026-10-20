-- Serialize acceptance on the booking even for writers outside the claim endpoint.
-- Capacity is per booking: multi-worker packages may accept exactly N different staff.
CREATE FUNCTION guard_booking_assignment_capacity() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE capacity integer; occupied integer;
BEGIN
    IF NEW.status <> 'ACCEPTED' THEN RETURN NEW; END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.status IN ('ACCEPTED','COMPLETED') AND OLD.booking_id = NEW.booking_id THEN
            RETURN NEW;
        END IF;
    END IF;
    SELECT required_staff_count INTO capacity FROM bookings WHERE id = NEW.booking_id FOR UPDATE;
    SELECT count(*) INTO occupied FROM booking_assignments
      WHERE booking_id = NEW.booking_id AND status IN ('ACCEPTED','COMPLETED') AND id <> NEW.id;
    IF occupied >= capacity THEN
        RAISE EXCEPTION 'Booking has no remaining staff positions' USING ERRCODE = '23514', CONSTRAINT = 'booking_assignment_capacity';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER booking_assignment_capacity
BEFORE INSERT OR UPDATE OF status, booking_id ON booking_assignments
FOR EACH ROW EXECUTE FUNCTION guard_booking_assignment_capacity();
