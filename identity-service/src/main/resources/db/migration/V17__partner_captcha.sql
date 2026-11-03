CREATE TABLE partner_captchas (
    id VARCHAR(36) PRIMARY KEY,
    answer_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX partner_captchas_expiry ON partner_captchas(expires_at);

-- Fixed global budgets also bound work when callers rotate addresses/tokens.
CREATE TABLE partner_request_budgets (
    operation VARCHAR(20) PRIMARY KEY,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0
);
INSERT INTO partner_request_budgets(operation, window_started_at) VALUES
    ('captcha', CURRENT_TIMESTAMP), ('application', CURRENT_TIMESTAMP);
