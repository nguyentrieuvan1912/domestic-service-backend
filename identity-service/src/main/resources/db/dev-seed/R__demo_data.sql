-- Synthetic local demo accounts. No real password hash, no working login credentials.
INSERT INTO users(id,user_name,password_hash,full_name,phone,email,role,status) VALUES
(1,'demo_admin','!DEMO_LOGIN_DISABLED!','Admin Demo','DEMO-ADMIN','admin@example.invalid','ADMIN','INACTIVE'),
(2,'demo_customer','!DEMO_LOGIN_DISABLED!','Customer Demo','DEMO-CUSTOMER','customer@example.invalid','CUSTOMER','INACTIVE'),
(3,'demo_staff','!DEMO_LOGIN_DISABLED!','Staff Demo','DEMO-STAFF','staff@example.invalid','STAFF','INACTIVE')
ON CONFLICT DO NOTHING;
INSERT INTO admins(id) VALUES (1) ON CONFLICT DO NOTHING;
INSERT INTO customers(id,gender,total_bookings) VALUES (2,'OTHER',3) ON CONFLICT DO NOTHING;
INSERT INTO staffs(id,gender,identity_number,profile_description,average_rating,total_reviews,experience_years,status,approved_by,approved_at)
VALUES (3,'OTHER','DEMO-ID-001','Synthetic staff for database inspection',5,1,2,'AVAILABLE',1,'2026-09-01T00:00:00Z') ON CONFLICT DO NOTHING;
INSERT INTO addresses(id,customer_id,title,receiver_name,receiver_phone,province,district,ward,detail_address,is_default)
VALUES (1,2,'Demo home','Customer Demo','DEMO-CUSTOMER','TP. Ho Chi Minh','Demo district','Demo ward','DEMO ADDRESS - not a real customer address',true) ON CONFLICT DO NOTHING;
INSERT INTO notifications(id,user_id,notification_type,title,content,reference_id,created_at)
VALUES (1,2,'SERVICE_COMPLETED','Demo booking completed','Synthetic notification; no message has been sent',1,'2026-10-01T03:00:00Z') ON CONFLICT DO NOTHING;

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
