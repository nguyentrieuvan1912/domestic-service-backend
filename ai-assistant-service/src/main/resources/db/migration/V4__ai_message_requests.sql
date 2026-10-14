ALTER TABLE ai_messages ADD COLUMN client_key VARCHAR(160);
CREATE UNIQUE INDEX uq_ai_message_client_key ON ai_messages(conversation_id, client_key) WHERE client_key IS NOT NULL;
