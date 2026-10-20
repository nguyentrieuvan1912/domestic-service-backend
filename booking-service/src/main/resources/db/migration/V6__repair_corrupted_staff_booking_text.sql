-- Repair only the known damaged snapshot values; preserve all other booking data.
-- Keep UTF-8 text in a migration instead of passing Vietnamese through shell encodings.
UPDATE bookings
SET service_snapshot = replace(replace(service_snapshot::text,
        'V??? sinh nh?? theo gi???', 'Vệ sinh nhà theo giờ'),
        'G??i ti??u chu???n 3 gi???', 'Gói tiêu chuẩn 3 giờ')::jsonb,
    address_snapshot = replace(replace(replace(address_snapshot::text,
        '123 Nguy???n Th??? Minh Khai, Ph?????ng B???n Th??nh, Qu???n 1',
        '123 Nguyễn Thị Minh Khai, Phường Bến Thành, Quận 1'),
        'Qu???n 1', 'Quận 1'), 'L?? Quang Vinh', 'Lê Quang Vinh')::jsonb
WHERE booking_code = 'BK-STAFF-2026';

-- This older snapshot uses aliases that staff assignment responses do not read.
-- Populate the canonical keys only when missing, retaining the original fields.
UPDATE bookings
SET address_snapshot = jsonb_strip_nulls(jsonb_build_object(
        'fullAddress', address_snapshot->'address',
        'recipientName', address_snapshot->'customerName',
        'recipientPhone', address_snapshot->'customerPhone')) || address_snapshot
WHERE booking_code = 'BK-STAFF-2026';

UPDATE booking_assignments
SET checklist = replace(replace(replace(checklist::text,
        'Ki???m tra ph??ng kh??ch v?? khu v???c l??m vi???c',
        'Kiểm tra phòng khách và khu vực làm việc'),
        'H??t b???i v?? lau s??n nh??', 'Hút bụi và lau sàn nhà'),
        'Lau d???n b??? m???t b??n gh???, k??? t???',
        'Lau dọn bề mặt bàn ghế, kệ tủ')::jsonb
WHERE booking_id IN (SELECT id FROM bookings WHERE booking_code = 'BK-STAFF-2026');
