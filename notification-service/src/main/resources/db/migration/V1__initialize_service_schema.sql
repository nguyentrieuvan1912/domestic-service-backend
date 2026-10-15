CREATE TABLE service_schema_metadata (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    service_name VARCHAR(80) NOT NULL,
    initialized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO service_schema_metadata(id, service_name) VALUES (1, 'notification-service');
