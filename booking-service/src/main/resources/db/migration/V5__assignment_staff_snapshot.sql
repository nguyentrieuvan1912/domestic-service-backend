ALTER TABLE booking_assignments ADD COLUMN staff_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb;
