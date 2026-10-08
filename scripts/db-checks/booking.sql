BEGIN;
DO $$
BEGIN
    INSERT INTO staff_reservations(staff_id, starts_at, ends_at, status)
    VALUES (3,'2030-01-01T01:00:00Z','2030-01-01T02:00:00Z','CONFIRMED');
    BEGIN
        INSERT INTO staff_reservations(staff_id, starts_at, ends_at, status)
        VALUES (3,'2030-01-01T01:30:00Z','2030-01-01T02:30:00Z','CONFIRMED');
        RAISE EXCEPTION 'TEST FAILED: overlapping staff reservation was allowed';
    EXCEPTION WHEN exclusion_violation THEN NULL;
    END;
    -- Adjacent intervals are valid because the exclusion range is [start,end).
    INSERT INTO staff_reservations(staff_id, starts_at, ends_at, status)
    VALUES (3,'2030-01-01T02:00:00Z','2030-01-01T03:00:00Z','CONFIRMED');
    BEGIN
        INSERT INTO reviews(booking_id,customer_id,staff_id,service_id,rating)
        VALUES (1,2,
                3,1,6);
        RAISE EXCEPTION 'TEST FAILED: invalid rating was allowed';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
END;
$$;
ROLLBACK;
