ALTER TABLE forgotten_job_remediations ADD COLUMN refund_synced BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE forgotten_job_remediations ADD COLUMN refund_amount BIGINT NOT NULL DEFAULT 0 CHECK(refund_amount>=0);
