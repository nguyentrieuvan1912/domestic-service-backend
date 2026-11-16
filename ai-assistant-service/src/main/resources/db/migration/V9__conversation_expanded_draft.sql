ALTER TABLE ai_conversations ADD COLUMN expanded_draft_id BIGINT
    REFERENCES booking_drafts(id) ON DELETE SET NULL;

UPDATE ai_conversations c
SET expanded_draft_id = (
    SELECT d.id FROM booking_drafts d
    WHERE d.conversation_id = c.id AND d.customer_id = c.customer_id
        AND d.status = 'COLLECTING' AND d.expires_at > now() AND d.draft_data->>'serviceId' IS NOT NULL
    ORDER BY d.updated_at DESC, d.id DESC LIMIT 1
)
WHERE c.status = 'ACTIVE';
