ALTER TABLE ai_conversations ADD COLUMN pending_draft_switch JSONB;

UPDATE booking_drafts
SET status = 'EXPIRED'
WHERE status = 'COLLECTING' AND expires_at <= now();

WITH ranked AS (
    SELECT id, row_number() OVER (
        PARTITION BY conversation_id
        ORDER BY updated_at DESC, id DESC
    ) AS position
    FROM booking_drafts
    WHERE status = 'COLLECTING'
)
UPDATE booking_drafts d
SET status = 'CANCELLED', version = version + 1
FROM ranked r
WHERE d.id = r.id AND r.position > 1;

UPDATE ai_conversations c
SET expanded_draft_id = NULL
WHERE EXISTS (
    SELECT 1 FROM booking_drafts d
    WHERE d.id = c.expanded_draft_id AND d.status <> 'COLLECTING'
);

CREATE UNIQUE INDEX uq_booking_drafts_active_conversation
    ON booking_drafts (conversation_id) WHERE status = 'COLLECTING';
