ALTER TABLE bookings ADD COLUMN staff_response_deadline TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN staff_wait_state VARCHAR(10) CHECK (staff_wait_state IN ('WAITING','EXPIRED','CLOSED'));
ALTER TABLE bookings ADD COLUMN staff_wait_round INTEGER NOT NULL DEFAULT 0;
CREATE INDEX ix_booking_response_deadline ON bookings(staff_response_deadline)
    WHERE staff_wait_state='WAITING' AND status='STAFF_ASSIGNED';

-- Give existing invitations a full response window when this feature is installed.
UPDATE bookings SET staff_response_deadline=now()+interval '3 minutes',staff_wait_state='WAITING',staff_wait_round=1
WHERE booking_mode='MODE_A' AND status='STAFF_ASSIGNED' AND pricing_confirmed=true;
