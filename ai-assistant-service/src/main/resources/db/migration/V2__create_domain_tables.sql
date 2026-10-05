CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ENDED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ
);
CREATE INDEX ix_ai_conversations_customer ON ai_conversations(customer_id, started_at DESC);
CREATE TABLE ai_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id),
    sender_type VARCHAR(20) NOT NULL CHECK (sender_type IN ('USER','ASSISTANT','SYSTEM','TOOL')),
    content TEXT NOT NULL,
    suggestions JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_messages_conversation ON ai_messages(conversation_id, created_at);
CREATE TABLE booking_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id),
    customer_id UUID NOT NULL,
    draft_data JSONB NOT NULL DEFAULT '{}',
    missing_slots JSONB NOT NULL DEFAULT '[]',
    preview_snapshot JSONB,
    quote_id UUID,
    booking_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'COLLECTING'
        CHECK (status IN ('COLLECTING','READY','CONFIRMED','EXPIRED','CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (expires_at > created_at),
    CHECK (status <> 'CONFIRMED' OR (confirmed_at IS NOT NULL AND booking_id IS NOT NULL))
);
CREATE TABLE ai_tool_calls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES ai_messages(id),
    draft_id UUID REFERENCES booking_drafts(id),
    tool_name VARCHAR(100) NOT NULL,
    arguments JSONB NOT NULL DEFAULT '{}',
    result JSONB,
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED','SUCCEEDED','FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE knowledge_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    source_uri TEXT,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE knowledge_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES knowledge_documents(id),
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    content TEXT NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    embedding JSONB CHECK (embedding IS NULL OR jsonb_typeof(embedding) = 'array'),
    embedding_model VARCHAR(100),
    UNIQUE(document_id, chunk_index)
);
CREATE INDEX ix_knowledge_chunks_search ON knowledge_chunks USING gin(search_vector);
-- JSON embeddings are a placeholder; vector index/provider choice belongs to AI implementation.
CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL,
    operation VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_status INTEGER CHECK (response_status BETWEEN 100 AND 599),
    response_body JSONB,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING','COMPLETED','FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE(actor_id, operation, idempotency_key),
    CHECK (expires_at > created_at)
);
