ALTER TABLE conversations ADD COLUMN customer_trashed_at TIMESTAMPTZ;
ALTER TABLE conversations ADD COLUMN customer_deleted_through_id BIGINT NOT NULL DEFAULT 0 CHECK (customer_deleted_through_id >= 0);
ALTER TABLE conversations ADD COLUMN customer_deleted_at TIMESTAMPTZ;
CREATE INDEX ix_conversations_customer_trash ON conversations(customer_id, customer_trashed_at);
