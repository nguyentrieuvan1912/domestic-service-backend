ALTER TABLE payments ADD COLUMN admin_note TEXT NOT NULL DEFAULT '';
ALTER TABLE withdrawals ADD COLUMN request_key VARCHAR(160);
CREATE UNIQUE INDEX uq_withdrawal_request ON withdrawals(staff_id,request_key) WHERE request_key IS NOT NULL;
CREATE TABLE finance_settings(name VARCHAR(80) PRIMARY KEY,number_value NUMERIC(10,2) NOT NULL,updated_by BIGINT NOT NULL,updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
ALTER TABLE penalties ADD COLUMN request_key VARCHAR(160);
CREATE UNIQUE INDEX uq_penalty_request ON penalties(staff_id,request_key) WHERE request_key IS NOT NULL;
CREATE UNIQUE INDEX uq_reward_period ON rewards(staff_id,reward_type,period_start,period_end);
