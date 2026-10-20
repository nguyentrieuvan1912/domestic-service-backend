ALTER TABLE price_quotes ADD COLUMN request_key VARCHAR(160);
ALTER TABLE price_quotes ADD COLUMN request_hash VARCHAR(128);
ALTER TABLE price_quotes ADD COLUMN consumed_booking_id BIGINT UNIQUE;
ALTER TABLE price_quotes ADD COLUMN released_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_quote_request ON price_quotes(customer_id,request_key) WHERE request_key IS NOT NULL;
