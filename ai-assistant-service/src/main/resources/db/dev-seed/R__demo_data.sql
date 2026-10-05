INSERT INTO ai_conversations(id,customer_id,title,status,started_at,ended_at)
VALUES ('50000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','Demo AI booking','ENDED','2026-09-30T00:00:00Z','2026-09-30T01:00:00Z') ON CONFLICT DO NOTHING;
INSERT INTO ai_messages(id,conversation_id,sender_type,content,suggestions) VALUES
('50000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000001','USER','Demo: dat don dep 2 gio','[]'),
('50000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000001','ASSISTANT','Demo preview; no real AI request was made','[{"type":"SERVICE","referenceId":"20000000-0000-0000-0000-000000000101"}]') ON CONFLICT DO NOTHING;
INSERT INTO booking_drafts(id,conversation_id,customer_id,draft_data,preview_snapshot,quote_id,booking_id,status,created_at,expires_at,confirmed_at)
VALUES ('50000000-0000-0000-0000-000000000004','50000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','{"demo":true,"serviceId":"20000000-0000-0000-0000-000000000101","areaM2":60}','{"demo":true,"totalAmount":200000}','20000000-0000-0000-0000-000000000601','30000000-0000-0000-0000-000000000001','CONFIRMED','2026-09-30T00:00:00Z','2026-09-30T01:00:00Z','2026-09-30T00:30:00Z') ON CONFLICT DO NOTHING;
INSERT INTO ai_tool_calls(id,message_id,draft_id,tool_name,arguments,result,status)
VALUES ('50000000-0000-0000-0000-000000000005','50000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000004','preview_booking','{"demo":true}','{"demo":true,"totalAmount":200000}','SUCCEEDED') ON CONFLICT DO NOTHING;
INSERT INTO knowledge_documents(id,title,content)
VALUES ('50000000-0000-0000-0000-000000000006','Demo service FAQ','Synthetic FAQ: bookings require a service, an address, a time and customer confirmation.') ON CONFLICT DO NOTHING;
INSERT INTO knowledge_chunks(id,document_id,chunk_index,content)
VALUES ('50000000-0000-0000-0000-000000000007','50000000-0000-0000-0000-000000000006',0,'Bookings require customer confirmation. AI cannot confirm payment or change service prices.') ON CONFLICT DO NOTHING;
