CREATE TABLE customer_favorites (
 customer_id BIGINT NOT NULL REFERENCES customers(id),
 service_id BIGINT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(customer_id,service_id)
);
