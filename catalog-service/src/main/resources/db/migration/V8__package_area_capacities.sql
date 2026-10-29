-- Promote the finite limits already published in package descriptions into structured policy.
-- Do not infer a finite limit from an open-ended description such as "> 100m²".
WITH limits(category_code, package_name, max_area) AS (VALUES
 ('CLEANING_HOURLY','Gói 2 giờ',55),
 ('CLEANING_HOURLY','Gói 2.5 giờ',70),
 ('CLEANING_HOURLY','Gói 3 giờ',85),
 ('CLEANING_HOURLY','Gói 3.5 giờ',100),
 ('CLEANING_DEEP','Tổng vệ sinh căn hộ (< 60m²)',60),
 ('CLEANING_DEEP','Tổng vệ sinh căn hộ tiêu chuẩn (60–90m²)',90),
 ('CLEANING_DEEP','Tổng vệ sinh nhà phố (90–130m²)',130),
 ('OTHER_SERVICES','Phun sương ULV khử khuẩn (< 80m²)',80)
)
UPDATE service_packages p SET max_area=l.max_area,updated_at=now()
FROM services s JOIN service_categories c ON c.id=s.category_id JOIN limits l ON l.category_code=c.category_code
WHERE p.service_id=s.id AND p.package_name=l.package_name AND p.max_area IS NULL;
