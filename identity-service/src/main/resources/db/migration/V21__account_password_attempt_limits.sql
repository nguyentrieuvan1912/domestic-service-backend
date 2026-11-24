ALTER TABLE users
    ADD COLUMN password_failure_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN password_failure_window TIMESTAMP WITH TIME ZONE;
