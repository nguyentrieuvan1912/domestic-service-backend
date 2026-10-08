INSERT INTO ai_conversations(id,customer_id,title,status,started_at,ended_at)
VALUES (1,2,'Demo AI booking','ENDED','2026-09-30T00:00:00Z','2026-09-30T01:00:00Z') ON CONFLICT DO NOTHING;
INSERT INTO ai_messages(id,conversation_id,sender_type,content,suggestions) VALUES
(2,1,'USER','Demo: dat don dep 2 gio','[]'),
(3,1,'ASSISTANT','Demo preview; no real AI request was made','[{"type":"SERVICE","referenceId":"1"}]') ON CONFLICT DO NOTHING;
INSERT INTO booking_drafts(id,conversation_id,customer_id,draft_data,preview_snapshot,quote_id,booking_id,status,created_at,expires_at,confirmed_at)
VALUES (4,1,2,'{"demo":true,"serviceId":"1","areaM2":60}','{"demo":true,"totalAmount":200000}',1,1,'CONFIRMED','2026-09-30T00:00:00Z','2026-09-30T01:00:00Z','2026-09-30T00:30:00Z') ON CONFLICT DO NOTHING;
INSERT INTO ai_tool_calls(id,message_id,draft_id,tool_name,arguments,result,status)
VALUES (5,3,4,'preview_booking','{"demo":true}','{"demo":true,"totalAmount":200000}','SUCCEEDED') ON CONFLICT DO NOTHING;
INSERT INTO knowledge_documents(id,title,content)
VALUES (6,'Demo service FAQ','Synthetic FAQ: bookings require a service, an address, a time and customer confirmation.') ON CONFLICT DO NOTHING;
INSERT INTO knowledge_chunks(id,document_id,chunk_index,content)
VALUES (7,6,0,'Bookings require customer confirmation. AI cannot confirm payment or change service prices.') ON CONFLICT DO NOTHING;

-- Explicit fixture IDs must also advance identity sequences on a freshly cloned database.
DO $$
DECLARE col RECORD; sequence_name TEXT; next_id BIGINT; sequence_next BIGINT;
BEGIN
    FOR col IN SELECT table_name FROM information_schema.columns
        WHERE table_schema = 'public' AND column_name = 'id' AND is_identity = 'YES'
    LOOP
        sequence_name := pg_get_serial_sequence('public.' || quote_ident(col.table_name), 'id');
        EXECUTE format('SELECT coalesce(max(id), 0) + 1 FROM public.%I', col.table_name) INTO next_id;
        EXECUTE format('SELECT last_value + CASE WHEN is_called THEN 1 ELSE 0 END FROM %s', sequence_name)
            INTO sequence_next;
        PERFORM setval(sequence_name::regclass, greatest(next_id, sequence_next), false);
    END LOOP;
END;
$$;
