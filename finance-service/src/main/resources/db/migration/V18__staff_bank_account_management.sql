ALTER TABLE staff_bank_accounts ADD COLUMN unlinked_at TIMESTAMPTZ;

UPDATE staff_bank_accounts
SET is_default = false
WHERE is_default AND status <> 'VERIFIED';

UPDATE staff_bank_accounts bank
SET is_default = true
FROM (
    SELECT DISTINCT ON (candidate.staff_id) candidate.id
    FROM staff_bank_accounts candidate
    WHERE candidate.status = 'VERIFIED'
      AND NOT EXISTS (
          SELECT 1 FROM staff_bank_accounts preferred
          WHERE preferred.staff_id = candidate.staff_id AND preferred.is_default
      )
    ORDER BY candidate.staff_id, candidate.id
) first_verified
WHERE bank.id = first_verified.id;

ALTER TABLE staff_bank_accounts
    ADD CONSTRAINT ck_staff_bank_default_verified CHECK (NOT is_default OR status = 'VERIFIED'),
    ADD CONSTRAINT ck_staff_bank_unlinked_inactive CHECK (unlinked_at IS NULL OR status = 'INACTIVE');

CREATE INDEX ix_withdrawals_active_bank ON withdrawals(staff_id, bank_account_id)
    WHERE status IN ('PENDING', 'APPROVED', 'PROCESSING');
