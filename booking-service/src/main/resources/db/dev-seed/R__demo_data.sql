INSERT INTO bookings(id,booking_code,booking_mode,status,customer_id,service_id,package_id,address_id,quote_id,quote_version,service_snapshot,address_snapshot,starts_at,ends_at,base_amount,add_on_amount,discount_amount,total_amount,payment_method,payment_status,created_at,completed_at,cancelled_at,cancel_reason)
VALUES
(1,'DEMO-BK-001','MODE_A','COMPLETED',2,1,1,1,1,1,'{"demo":true,"serviceName":"Don dep nha theo gio","packageName":"Demo 2 hours"}','{"demo":true,"address":"DEMO ADDRESS"}','2026-10-01T01:00:00Z','2026-10-01T03:00:00Z',180000,20000,0,200000,'BANK_TRANSFER_QR','PAID','2026-09-30T01:00:00Z','2026-10-01T03:00:00Z',NULL,NULL),
(2,'DEMO-BK-002','MODE_B','REFUNDED',2,1,1,1,NULL,NULL,'{"demo":true,"serviceName":"Don dep nha theo gio"}','{"demo":true,"address":"DEMO ADDRESS"}','2026-10-02T01:00:00Z','2026-10-02T03:00:00Z',180000,0,30000,150000,'BANK_TRANSFER_QR','REFUNDED','2026-10-01T04:00:00Z',NULL,'2026-10-01T05:00:00Z','Demo cancellation'),
(3,'DEMO-BK-003','MODE_B','STAFF_ASSIGNED',2,1,1,1,NULL,NULL,'{"demo":true,"serviceName":"Don dep nha theo gio"}','{"demo":true,"address":"DEMO ADDRESS"}','2026-10-10T01:00:00Z','2026-10-10T03:00:00Z',180000,0,0,180000,'BANK_TRANSFER_QR','PENDING','2026-10-05T01:00:00Z',NULL,NULL,NULL)
ON CONFLICT DO NOTHING;
INSERT INTO booking_add_ons(id,booking_id,add_on_id,name_snapshot,quantity,unit_price,total_price)
VALUES (1,1,1,'Demo extra cleaning',1,20000,20000) ON CONFLICT DO NOTHING;
INSERT INTO booking_requirement_answers(id,booking_id,requirement_id,field_key,label_snapshot,answer_value)
VALUES (1,1,1,'areaM2','Dien tich nha','60') ON CONFLICT DO NOTHING;
INSERT INTO booking_assignments(id,booking_id,staff_id,matching_score,status,assigned_at,accepted_at) VALUES
(1,1,3,95,'COMPLETED','2026-09-30T02:00:00Z','2026-09-30T03:00:00Z'),
(2,3,3,92,'ASSIGNED','2026-10-05T01:00:00Z',NULL) ON CONFLICT DO NOTHING;
INSERT INTO booking_status_histories(id,booking_id,old_status,new_status,changed_by,changed_by_type,reason,changed_at) VALUES
(1,1,'IN_PROGRESS','COMPLETED',3,'STAFF','Demo service completed','2026-10-01T03:00:00Z'),
(2,2,'CANCELLED','REFUNDED',NULL,'SYSTEM','Demo refund completed','2026-10-01T06:00:00Z') ON CONFLICT DO NOTHING;
INSERT INTO reviews(id,booking_id,customer_id,staff_id,service_id,rating,comment)
VALUES (1,1,2,3,1,5,'Demo review - not a real customer review') ON CONFLICT DO NOTHING;
INSERT INTO staff_areas(id,staff_id,province,district,is_primary)
VALUES (1,3,'TP. Ho Chi Minh','Demo district',true) ON CONFLICT DO NOTHING;
INSERT INTO staff_availabilities(id,staff_id,day_of_week,start_time,end_time)
VALUES (1,3,6,'08:00','17:00') ON CONFLICT DO NOTHING;
INSERT INTO staff_service_capabilities(id,staff_id,service_id,skill_level,experience_years,status,approved_at,approved_by,note)
VALUES (1,3,1,'ADVANCED',2,'APPROVED','2026-09-01T00:00:00Z',1,'Demo capability') ON CONFLICT DO NOTHING;
INSERT INTO staff_restrictions(id,staff_id,restriction_type,reason,starts_at,ends_at,status,created_by,resolved_at)
VALUES (1,3,'WARNING','Demo resolved warning','2026-09-01T00:00:00Z','2026-09-02T00:00:00Z','REVOKED',1,'2026-09-02T00:00:00Z') ON CONFLICT DO NOTHING;
INSERT INTO conversations(id,booking_id,customer_id,staff_id,status)
VALUES (1,1,2,3,'CLOSED') ON CONFLICT DO NOTHING;
INSERT INTO messages(id,conversation_id,sender_id,sender_type,message_type,content)
VALUES (1,1,2,'CUSTOMER','TEXT','Demo message - this was not sent to anyone') ON CONFLICT DO NOTHING;
INSERT INTO staff_reservations(id,staff_id,booking_id,starts_at,ends_at,status)
VALUES (1,3,3,'2026-10-10T01:00:00Z','2026-10-10T03:00:00Z','CONFIRMED') ON CONFLICT DO NOTHING;

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
