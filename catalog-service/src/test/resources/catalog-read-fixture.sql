-- Isolated H2 read-model fixture, not a production migration.
DROP ALL OBJECTS;
CREATE TABLE service_categories(id BIGINT PRIMARY KEY, category_code VARCHAR, category_name VARCHAR,
 description VARCHAR, icon_url VARCHAR, category_group VARCHAR, status VARCHAR);
CREATE TABLE services(id BIGINT PRIMARY KEY, category_id BIGINT, service_code VARCHAR, service_name VARCHAR,
 description VARCHAR, short_description VARCHAR, service_type VARCHAR, price_unit VARCHAR, base_price BIGINT,
 estimated_duration_minutes INT, requires_qualification BOOLEAN, image_url VARCHAR, highlights VARCHAR,
 workflow VARCHAR, benefits VARCHAR, status VARCHAR);
CREATE TABLE service_packages(id BIGINT PRIMARY KEY, service_id BIGINT, package_name VARCHAR, description VARCHAR,
 duration_minutes INT, base_price BIGINT, default_staff_count INT, max_area NUMERIC, status VARCHAR);
CREATE TABLE add_ons(id BIGINT PRIMARY KEY, service_id BIGINT, name VARCHAR, description VARCHAR, price BIGINT,
 extra_duration_minutes INT, image_url VARCHAR, status VARCHAR);
CREATE TABLE service_requirements(id BIGINT PRIMARY KEY, service_id BIGINT, field_key VARCHAR, label VARCHAR,
 field_type VARCHAR, required BOOLEAN, options VARCHAR, validation_rules VARCHAR, display_order INT, status VARCHAR);
INSERT INTO service_categories VALUES(1,'CLEANING','Vệ sinh',NULL,NULL,'CLEANING','ACTIVE'),
 (2,'HIDDEN','Hidden',NULL,NULL,NULL,'INACTIVE'),(3,'CARE','Chăm sóc',NULL,NULL,'CARE','ACTIVE');
INSERT INTO services VALUES
 (1,1,'CLEAN','Dọn nhà','Dọn nhà sạch','Dọn nhà','CLEANING_HOURLY','PACKAGE',180000,120,false,NULL,'["Linh hoạt"]','["Chuẩn bị","Vệ sinh"]','["Tiết kiệm thời gian"]','ACTIVE'),
 (2,1,'DISABLED','Disabled',NULL,NULL,'GENERAL','PACKAGE',100000,60,false,NULL,'[]','[]','[]','INACTIVE'),
 (3,2,'HIDDEN','Hidden category',NULL,NULL,'GENERAL','PACKAGE',100000,60,false,NULL,'[]','[]','[]','ACTIVE'),
 (4,3,'CARE','Chăm sóc bé',NULL,NULL,'CHILD_CARE','HOUR',120000,60,true,NULL,'[]','[]','[]','ACTIVE');
INSERT INTO service_packages VALUES(1,1,'2 giờ',NULL,120,180000,1,60,'ACTIVE'),
 (2,1,'Hidden package',NULL,240,360000,1,NULL,'INACTIVE');
INSERT INTO add_ons VALUES(1,1,'Lau bếp',NULL,20000,15,NULL,'ACTIVE'),
 (2,1,'Hidden addon',NULL,10000,5,NULL,'INACTIVE');
INSERT INTO service_requirements VALUES(1,1,'areaM2','Diện tích','NUMBER',true,'[]','{"min":1,"max":200}',1,'ACTIVE'),
 (2,1,'hidden','Hidden','TEXT',false,'[]','{}',0,'INACTIVE');
