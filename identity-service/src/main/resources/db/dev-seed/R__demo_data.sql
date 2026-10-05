-- Synthetic local demo accounts. No real password hash, no working login credentials.
INSERT INTO users(id,user_name,password_hash,full_name,phone,email,role,status) VALUES
('10000000-0000-0000-0000-000000000001','demo_admin','!DEMO_LOGIN_DISABLED!','Admin Demo','DEMO-ADMIN','admin@example.invalid','ADMIN','INACTIVE'),
('10000000-0000-0000-0000-000000000002','demo_customer','!DEMO_LOGIN_DISABLED!','Customer Demo','DEMO-CUSTOMER','customer@example.invalid','CUSTOMER','INACTIVE'),
('10000000-0000-0000-0000-000000000003','demo_staff','!DEMO_LOGIN_DISABLED!','Staff Demo','DEMO-STAFF','staff@example.invalid','STAFF','INACTIVE')
ON CONFLICT DO NOTHING;
INSERT INTO admins(id) VALUES ('10000000-0000-0000-0000-000000000001') ON CONFLICT DO NOTHING;
INSERT INTO customers(id,gender,total_bookings) VALUES ('10000000-0000-0000-0000-000000000002','OTHER',3) ON CONFLICT DO NOTHING;
INSERT INTO staffs(id,gender,identity_number,profile_description,average_rating,total_reviews,experience_years,status,approved_by,approved_at)
VALUES ('10000000-0000-0000-0000-000000000003','OTHER','DEMO-ID-001','Synthetic staff for database inspection',5,1,2,'AVAILABLE','10000000-0000-0000-0000-000000000001','2026-09-01T00:00:00Z') ON CONFLICT DO NOTHING;
INSERT INTO addresses(id,customer_id,title,receiver_name,receiver_phone,province,district,ward,detail_address,is_default)
VALUES ('10000000-0000-0000-0000-000000000101','10000000-0000-0000-0000-000000000002','Demo home','Customer Demo','DEMO-CUSTOMER','TP. Ho Chi Minh','Demo district','Demo ward','DEMO ADDRESS - not a real customer address',true) ON CONFLICT DO NOTHING;
INSERT INTO notifications(id,user_id,notification_type,title,content,reference_id,created_at)
VALUES ('10000000-0000-0000-0000-000000000201','10000000-0000-0000-0000-000000000002','SERVICE_COMPLETED','Demo booking completed','Synthetic notification; no message has been sent','30000000-0000-0000-0000-000000000001','2026-10-01T03:00:00Z') ON CONFLICT DO NOTHING;
