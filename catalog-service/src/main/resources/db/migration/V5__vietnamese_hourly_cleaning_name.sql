UPDATE services
SET service_name = 'Dọn dẹp nhà theo giờ', updated_at = now(), version = version + 1
WHERE service_code = 'DEMO-CLEANING' AND service_name = 'Don dep nha theo gio';
