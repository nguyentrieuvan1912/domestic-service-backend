# Cấu trúc code backend và cách đối chiếu sơ đồ lớp

Backend giữ kiến trúc microservices, mỗi service sở hữu database của mình. Bên trong service, code được chia thành các tầng:

```text
src/main/java/com/domesticservice/<service>/
├── <Name>ServiceApplication.java
├── controller/         HTTP endpoint, tham số, validation đầu vào, status code
├── dto/                Request/response của API
├── service/            Nghiệp vụ, phân quyền, transaction, điều phối sự kiện
├── entity/             Lớp JPA ánh xạ bảng và quan hệ trong database của service
├── repository/         Spring Data JPA và các truy vấn nghiệp vụ/native
│   └── support/        Cầu nối native query và JDBC dùng chung nội bộ service
└── config/             Cấu hình và bootstrap

src/main/resources/
├── application.yml
└── db/migration/        Lịch sử thay đổi schema bằng Flyway
src/test/               Kiểm thử hành vi và ánh xạ
```

`application/` trước đây được đổi thành `service/`. Các lớp khởi động vẫn ở package gốc để Spring quét các tầng bên dưới. Gateway chỉ định tuyến và cấu hình HTTP, không có Entity/database. `common-web` chỉ giữ tiện ích HTTP; Entity và Repository không dùng chung giữa các service.

## Đi từ sơ đồ lớp đến code

Ví dụ lớp `Customer`:

- `identity-service/.../entity/User.java`: các trường tài khoản dùng chung.
- `identity-service/.../entity/Customer.java`: kế thừa User bằng `@Inheritance(JOINED)` và khóa chính chung với bảng users.
- `identity-service/.../entity/Address.java`: quan hệ địa chỉ với Customer.
- `identity-service/.../repository/CustomerRepository.java`: Spring Data JPA cho Customer.
- `identity-service/.../repository/AccountSqlRepository.java`: truy vấn tài khoản và ánh xạ response của luồng hiện có.
- `identity-service/.../service/AccountService.java`: đăng nhập, đăng ký, hồ sơ và quyền truy cập.
- `identity-service/.../dto/AccountRequests.java`, `AccountResponse.java`: dữ liệu vào/ra API.
- `identity-service/.../controller/AccountController.java`: các đường dẫn HTTP.

[Bảng đối chiếu từng Entity, bảng và Repository](CLASS_CODE_MAPPING.md) bao gồm 44 lớp của bộ sơ đồ `Diagram/ClassDiagramSimple` và các lớp bổ sung từ schema đang chạy. Tổng cộng có 70 Entity theo bảng nghiệp vụ/kỹ thuật của sáu database; không tính Flyway và bảng metadata khởi tạo service.

## Quy tắc ánh xạ JPA

- ID bản ghi là `Long`, tự tăng theo schema. Admin/Customer/Staff kế thừa ID của User; không sinh ID riêng cho hồ sơ con.
- Các bảng có khóa ghép dùng `@IdClass` và lớp ID có `equals`/`hashCode`.
- Khóa ngoại nội bộ database dùng `@ManyToOne`, `@OneToOne`, `@OneToMany`, `@JoinColumn` hoặc `@JoinColumns`. Quan hệ nhiều–nhiều như Promotion–Service được thể hiện bằng Entity bảng nối `PromotionService` và khóa ghép.
- Các cột FK giữ giá trị ID ghi dữ liệu. Thuộc tính quan hệ dùng cùng cột với `insertable=false, updatable=false`; setter quan hệ đồng bộ ID, còn collection phía cha là góc nhìn `mappedBy`. Khi thêm liên kết, đặt FK hoặc gọi setter trên Entity phía con rồi lưu Entity phía con; không chỉ thêm phần tử vào collection phía cha.
- Quan hệ tải lười; không thêm cascade xóa hoặc orphan removal vào dữ liệu hiện có. Entity không được trả trực tiếp từ HTTP API.
- Các cột version được ánh xạ `@Version`; dữ liệu tiền dùng Long/BigDecimal theo schema; JSONB dùng ánh xạ JSON; currency CHAR dùng JDBC type CHAR.
- Quan hệ sang service khác giữ ID và giao tiếp qua HTTP. Ví dụ Booking giữ `customerId`, không khai báo `@ManyToOne Customer` từ identity-service. Khóa ngoại ghép kiểm tra chủ ví/tài khoản ngân hàng/thiết bị vẫn được ánh xạ trong database sở hữu.
- Hibernate dùng `ddl-auto=validate`; Flyway tiếp tục quản lý schema. Không sửa migration đã áp dụng và không dùng Hibernate để tạo lại bảng dữ liệu đang chạy.
- Trạng thái hiện lưu bằng String theo CHECK constraint hiện có; việc thêm Entity không tự thay đổi hợp đồng trạng thái của API thành một bộ enum mới.

## Lombok trong Entity

Entity và lớp khóa ghép dùng `@Getter`, `@Setter`, `@NoArgsConstructor` để sinh accessor và constructor không tham số khi biên dịch. Các setter có logic đồng bộ quan hệ với ID khóa ngoại vẫn viết tường minh. Quan hệ phía cha vốn chỉ có getter dùng `@Setter(AccessLevel.NONE)` để giữ nguyên cách cập nhật liên kết qua phía con.

Không dùng `@Data`, `@ToString` hoặc thay đổi equals/hashCode của Entity trong lần rút gọn này. Các lớp khóa ghép giữ nguyên equals/hashCode hiện có. Lombok là dependency phục vụ biên dịch, không đóng gói trong executable jar; annotation processor được cấu hình chung và phiên bản theo Spring Boot BOM.

## Vì sao vẫn có native query và JDBC trong Repository?

Các nghiệp vụ hiện có dùng khóa hàng, PostgreSQL advisory lock, `ON CONFLICT`, `RETURNING`, JSONB, CTE và sổ bút toán. Những truy vấn này được chuyển vào các lớp `*SqlRepository`, giữ nội dung SQL và thứ tự gọi; không viết lại quy tắc nghiệp vụ trong lần tổ chức này.

`NativeQueryExecutor` thực thi phần lớn truy vấn/cập nhật bằng EntityManager native query. JDBC được giữ cho projection ResultSet, generated keys, advisory lock, lịch sử dụng SQL TIME và scalar ngày/giờ cần giữ nguyên biểu diễn của luồng cũ. Cả JPA và JDBC cùng dùng datasource và JpaTransactionManager của service, tham gia cùng transaction. Không mở transaction database chung giữa các service.

Đây là cấu trúc có **Entity và quan hệ JPA đầy đủ, kết hợp Repository native/JDBC cho nghiệp vụ hiện có**; không phải đã thay mọi SQL bằng thao tác CRUD của JpaRepository. Repository JPA theo từng Entity có thể được dùng cho CRUD mới; các thao tác khóa và ghi sổ hiện có tiếp tục đi qua Repository nghiệp vụ.

## Luồng ví dụ: lấy hồ sơ

```text
Mobile/Web → Gateway → AccountController
                           ↓
                     AccountService
                           ↓
                    AccountSqlRepository
                           ↓
                       identity_db
                           ↓
                 DTO / dữ liệu response cũ
                           ↓
                       Mobile/Web
```

Controller không truy cập database; Service không chứa SQL hoặc xử lý ResultSet. Repository truy vấn và ánh xạ kết quả; Service kiểm tra quyền và áp dụng nghiệp vụ. Các API và hình dạng JSON hiện có được giữ lại, bao gồm các response động đang dùng Map.

## Kiểm tra

Từ thư mục backend:

```powershell
.\mvnw.cmd -B --no-transfer-progress verify
python scripts/check-architecture.py
```

Kiểm tra ánh xạ trên PostgreSQL hiện có, chỉ bật khi chủ động chạy:

```powershell
$env:JPA_POSTGRES_VERIFY='true'
try {
    .\mvnw.cmd -B --no-transfer-progress '-Dtest=JpaPostgresMappingTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
} finally {
    Remove-Item Env:\JPA_POSTGRES_VERIFY -ErrorAction SilentlyContinue
}
```

Kiểm thử PostgreSQL chỉ khởi tạo tầng persistence, không bật HTTP, bootstrap tài khoản hoặc scheduler. Mọi thay đổi bản ghi trong kiểm thử được rollback; sequence ID có thể tăng theo hành vi thông thường của PostgreSQL. Test đọc các Entity và kiểm tra thêm/sửa/xóa trên schema hiện có. Không thay thế kiểm thử nghiệp vụ/API bằng việc chỉ khởi động được Hibernate.
