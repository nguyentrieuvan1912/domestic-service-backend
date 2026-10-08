INSERT INTO service_categories(id,category_code,category_name,description,category_group)
VALUES (1,'CLEANING_HOURLY','Don dep theo gio','Demo catalog','CLEANING') ON CONFLICT DO NOTHING;
INSERT INTO services(id,category_id,service_code,service_name,description,service_type,price_unit,base_price,estimated_duration_minutes)
VALUES (1,1,'DEMO-CLEANING','Don dep nha theo gio','Synthetic service for local development','CLEANING_HOURLY','PACKAGE',180000,120) ON CONFLICT DO NOTHING;
INSERT INTO service_packages(id,service_id,package_name,duration_minutes,base_price,default_staff_count)
VALUES (1,1,'Demo 2 hours',120,180000,1) ON CONFLICT DO NOTHING;
INSERT INTO add_ons(id,service_id,name,price,extra_duration_minutes)
VALUES (1,1,'Demo extra cleaning',20000,0) ON CONFLICT DO NOTHING;
INSERT INTO service_requirements(id,service_id,field_key,label,field_type,required,validation_rules)
VALUES (1,1,'areaM2','Dien tich nha','NUMBER',true,'{"min":1,"max":200}') ON CONFLICT DO NOTHING;
INSERT INTO promotions(id,promotion_code,promotion_name,start_at,end_at,discount_type,discount_value,max_discount_amount,usage_limit)
VALUES (1,'DEMO10','Demo 10 percent','2026-01-01T00:00:00Z','2027-01-01T00:00:00Z','PERCENTAGE',10,50000,100) ON CONFLICT DO NOTHING;
INSERT INTO promotion_services(promotion_id,service_id) VALUES (1,1) ON CONFLICT DO NOTHING;
INSERT INTO price_quotes(id,customer_id,service_id,package_id,snapshot,base_amount,add_on_amount,total_amount,created_at,expires_at)
VALUES (1,2,1,1,'{"demo":true,"serviceName":"Don dep nha theo gio","areaM2":60}',180000,20000,200000,'2026-10-01T00:00:00Z','2026-10-01T00:30:00Z')
ON CONFLICT DO NOTHING;

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
