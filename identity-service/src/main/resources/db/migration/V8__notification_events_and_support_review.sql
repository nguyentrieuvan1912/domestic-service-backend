ALTER TABLE notifications ADD COLUMN event_key VARCHAR(200);
CREATE UNIQUE INDEX uq_notification_event ON notifications(user_id,event_key) WHERE event_key IS NOT NULL;
ALTER TABLE support_requests ADD COLUMN admin_note TEXT NOT NULL DEFAULT '';
ALTER TABLE support_requests ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
