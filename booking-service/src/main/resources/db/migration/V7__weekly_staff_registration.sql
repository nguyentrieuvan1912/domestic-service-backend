CREATE TABLE staff_schedule_weeks (
    staff_id BIGINT NOT NULL,
    week_start DATE NOT NULL CHECK (extract(isodow FROM week_start) = 1),
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (staff_id, week_start)
);

-- Freeze old recurring schedules into explicit dates for the two visible weeks.
INSERT INTO staff_availabilities (staff_id, available_date, start_time, end_time, timezone, status)
SELECT a.staff_id, d::date, a.start_time, a.end_time, a.timezone, a.status
FROM staff_availabilities a
CROSS JOIN generate_series(
    date_trunc('week', now() AT TIME ZONE 'Asia/Ho_Chi_Minh'),
    date_trunc('week', now() AT TIME ZONE 'Asia/Ho_Chi_Minh') + interval '13 days',
    interval '1 day') d
WHERE a.available_date IS NULL AND extract(dow FROM d) = a.day_of_week
  AND NOT EXISTS (SELECT 1 FROM staff_availabilities b WHERE b.staff_id=a.staff_id
      AND b.available_date=d::date AND b.start_time=a.start_time AND b.end_time=a.end_time);
DELETE FROM staff_availabilities WHERE available_date IS NULL;

-- Retain old areas as history, but never use districts for new matching/registration.
UPDATE staff_areas SET status='INACTIVE', is_primary=false
WHERE coalesce(ward, district, '') !~ '^(Phường |Xã |Đặc khu )';
