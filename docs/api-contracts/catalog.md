# Catalog API contract — read API v1

Owner: Người 1. Consumer: Web/Mobile, Booking/AI (người 2).
Chỉ các GET dưới đây đã triển khai; quote/Admin CRUD/promotion chưa triển khai.

## Public endpoints đã có

| Method/path (qua Gateway 8080) | Kết quả |
|---|---|
| GET /api/v1/catalog/categories | Array Category, chỉ ACTIVE, order name/id |
| GET /api/v1/catalog/services | Page; chỉ service ACTIVE thuộc category ACTIVE |
| GET /api/v1/catalog/services/{id} | Detail; không có hoặc INACTIVE trả 404 |

GET services: `q` tối đa 100 ký tự, trim, không phân biệt hoa/thường, tìm theo name/code/description.
Không bỏ dấu tiếng Việt. `%`, `_`, `!` là ký tự thường, không phải wildcard.
`categoryId` là ID số dương (không phải category code); mặc định tất cả.
`page` zero-based 0..100000, `size` 1..100, mặc định 12. Order service name/id ổn định.
Danh mục hợp lệ nhưng không có dịch vụ trả page rỗng; page vượt số trang cũng trả page rỗng.
Sai kiểu/tham số/ID không dương trả 400; lỗi dạng application/problem+json.

## DTO

- Category: `{id,code,name,description,iconUrl,group}`.
- ServiceSummary: `{id,categoryId,categoryCode,categoryName,code,name,description,shortDescription,
  serviceType,priceUnit,basePrice,estimatedDurationMinutes,requiresQualification,imageUrl,highlights}`.
- Page: `{items: ServiceSummary[],total,page,size,totalPages}`.
- Detail: `{service: ServiceSummary,packages,addOns,requirements,workflow,benefits}`.
- packages: `{id,name,description,durationMinutes,basePrice,defaultStaffCount,maxArea}`.
- addOns: `{id,name,description,price,extraDurationMinutes,imageUrl}`.
- requirements: `{id,fieldKey,label,fieldType,required,options,validationRules,displayOrder}`;
  options là array, validationRules là object. Order displayOrder/id.
- highlights/workflow/benefits là array string, có thể rỗng; metadata tùy chọn có thể null.
- ID JSON number, DB Long/BIGINT; frontend chuyển String(id) khi đưa vào route.
  Giá number VND nguyên, không phải USD/decimal; maxArea number m² hoặc null.
- Chỉ trả package/add-on/requirement ACTIVE thuộc chính service đang xem.
  Không lấy rating/reviews, khu vực phục vụ, matching hoặc AI bundle từ DB khác.

Ví dụ: `/api/v1/catalog/services?categoryId=1&q=DEMO&page=0&size=12`.
Demo hiện tại: service 1, package 1 giá 180000, add-on 1 giá 20000, requirement areaM2.
Profile dev-seed có thêm R__ui_catalog_data.sql: 16 nhóm, 16 dịch vụ mẫu Mobile và 3 dịch vụ
Web cùng gói/add-on, lấy từ dữ liệu UI hiện có. Giữ service/package/add-on demo ID 1 và các
tham chiếu Booking cũ. ID mới do database cấp; client tra theo code, không hard-code ID mới.
Code UI-srv-001..UI-srv-016 tương ứng prototype Mobile srv-001..srv-016; Web dùng
ONE_OFF_STANDARD, RECURRING_STANDARD, DEEP_CLEANING_STANDARD. Seed insert-only, không ghi đè
dịch vụ đã chỉnh sửa. Đây là dữ liệu mẫu local, không phải giá/chính sách production.

## Tích hợp frontend / cấu hình chung

- Repo Web giữ riêng: `.env.example` với `VITE_API_BASE_URL=http://localhost:8080`,
  dev port 5174, strictPort (không tự nhảy sang origin chưa cấu hình).
- Repo Mobile giữ riêng: `.env.example` với `EXPO_PUBLIC_API_BASE_URL=http://10.0.2.2:8080`
  cho Android emulator và `EXPO_PUBLIC_WEB_API_BASE_URL=http://localhost:8080` cho Expo Web
  8086, có thể chạy đồng thời. Nếu không đặt URL riêng Web thì dùng URL API chung. Native cần cấu hình explicit
  Gateway URL; không tự dùng localhost. Restart Expo sau khi thay biến môi trường nếu chưa reload.
- `.env.local` ở từng frontend là local/ignored; chỉ có public URL, không chứa DB credentials.
- Backend CORS allowlist local 5173/5174/8086, không wildcard. `.env` cũ thêm 5174 rồi recreate Gateway.
- Android emulator dùng 10.0.2.2; điện thoại thật cần host LAN IP và cấu hình mạng/Docker có chủ ý.
  Compose vẫn bind backend 127.0.0.1: chưa bật LAN exposure hoặc thay firewall.
- Giữ giao diện mẫu hiện có: banner, grid danh mục, card, tab và nút. Adapter chỉ map DTO vào
  view model cũ, không thay thiết kế. Web giữ menu/footer slug cũ và resolve slug qua service code;
  Mobile resolve srv-* cũ từ các prototype sang ID số trước khi gọi Catalog API.
- Web `/services`, `/services/{id}` và card trên Home đọc API; Mobile Home, `/catalog`,
  `/service/{id}` đọc API. Tab `/services` vẫn là Cộng đồng.
- Loading/empty/error/retry; timeout 12s, abort request khi rời màn hình/đổi filter;
  không retry vô hạn, không fallback sang mock hoặc service mặc định.
- Chỉ nguồn dữ liệu Catalog đã nối. Các nút Booking và các khối đánh giá/vùng phục vụ/AI giữ
  prototype hiện có của người 2; chưa phải API thật, không coi là đã hoàn thành hoặc xác nhận
  booking/thanh toán. Metadata trình bày chưa có trong DTO vẫn giữ trong template frontend.
  Admin CRUD, trang add-ons giới thiệu riêng và các tính năng ngoài Catalog chưa nối API.
- Khi người 2 nối Booking API, payload phải dùng ID số Catalog thực; không gửi srv-/pkg- của
  prototype. Adapter legacy hiện tại chỉ phục vụ điều hướng màn hình mẫu, không có POST Booking.

## Nghiệp vụ còn chờ

POST quotes cần serviceId, package/add-on và requirement đã validate, currency VND,
amount, duration, quoteVersion/expiresAt; Booking lưu snapshot. Chưa có endpoint này.
Không dùng basePrice/tổng cộng frontend gửi làm nguồn xác nhận thanh toán.
Không sửa migration/schema/Class Diagram trong chức năng này; dùng các bảng V2/V3 đã có.
System/info vẫn báo scaffold vì service còn nhiều nghiệp vụ chưa làm.
