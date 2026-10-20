INSERT INTO promotions (promotion_code,promotion_name,description,start_at,end_at,discount_type,discount_value,minimum_booking_amount,status)
VALUES
('GIAM10','Giảm 10% mọi dịch vụ','Giảm 10% tổng hóa đơn cho tất cả dịch vụ, không yêu cầu giá trị tối thiểu và không giới hạn số tiền giảm.',now()-interval '1 minute',now()+interval '1 year','PERCENTAGE',10,0,'ACTIVE');

INSERT INTO promotions (promotion_code,promotion_name,description,start_at,end_at,discount_type,discount_value,minimum_booking_amount,status)
SELECT 'VESINH10','Giảm 10% vệ sinh nhà theo giờ','Giảm 10% tổng hóa đơn có dịch vụ chính là Vệ sinh nhà theo giờ; không yêu cầu giá trị tối thiểu và không giới hạn số tiền giảm.',now()-interval '1 minute',now()+interval '1 year','PERCENTAGE',10,0,'ACTIVE'
WHERE EXISTS (SELECT 1 FROM services WHERE service_code='UI-srv-001');

INSERT INTO promotion_services (promotion_id,service_id)
SELECT p.id,s.id FROM promotions p CROSS JOIN services s
WHERE p.promotion_code='VESINH10' AND s.service_code='UI-srv-001';
