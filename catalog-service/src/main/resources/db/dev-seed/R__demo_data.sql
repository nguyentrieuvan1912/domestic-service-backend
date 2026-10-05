INSERT INTO service_categories(id,category_code,category_name,description,category_group)
VALUES ('20000000-0000-0000-0000-000000000001','CLEANING_HOURLY','Don dep theo gio','Demo catalog','CLEANING') ON CONFLICT DO NOTHING;
INSERT INTO services(id,category_id,service_code,service_name,description,service_type,price_unit,base_price,estimated_duration_minutes)
VALUES ('20000000-0000-0000-0000-000000000101','20000000-0000-0000-0000-000000000001','DEMO-CLEANING','Don dep nha theo gio','Synthetic service for local development','CLEANING_HOURLY','PACKAGE',180000,120) ON CONFLICT DO NOTHING;
INSERT INTO service_packages(id,service_id,package_name,duration_minutes,base_price,default_staff_count)
VALUES ('20000000-0000-0000-0000-000000000201','20000000-0000-0000-0000-000000000101','Demo 2 hours',120,180000,1) ON CONFLICT DO NOTHING;
INSERT INTO add_ons(id,service_id,name,price,extra_duration_minutes)
VALUES ('20000000-0000-0000-0000-000000000301','20000000-0000-0000-0000-000000000101','Demo extra cleaning',20000,0) ON CONFLICT DO NOTHING;
INSERT INTO service_requirements(id,service_id,field_key,label,field_type,required,validation_rules)
VALUES ('20000000-0000-0000-0000-000000000401','20000000-0000-0000-0000-000000000101','areaM2','Dien tich nha','NUMBER',true,'{"min":1,"max":200}') ON CONFLICT DO NOTHING;
INSERT INTO promotions(id,promotion_code,promotion_name,start_at,end_at,discount_type,discount_value,max_discount_amount,usage_limit)
VALUES ('20000000-0000-0000-0000-000000000501','DEMO10','Demo 10 percent','2026-01-01T00:00:00Z','2027-01-01T00:00:00Z','PERCENTAGE',10,50000,100) ON CONFLICT DO NOTHING;
INSERT INTO promotion_services(promotion_id,service_id) VALUES ('20000000-0000-0000-0000-000000000501','20000000-0000-0000-0000-000000000101') ON CONFLICT DO NOTHING;
INSERT INTO price_quotes(id,customer_id,service_id,package_id,snapshot,base_amount,add_on_amount,total_amount,created_at,expires_at)
VALUES ('20000000-0000-0000-0000-000000000601','10000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000101','20000000-0000-0000-0000-000000000201','{"demo":true,"serviceName":"Don dep nha theo gio","areaM2":60}',180000,20000,200000,'2026-10-01T00:00:00Z','2026-10-01T00:30:00Z')
ON CONFLICT DO NOTHING;
