WITH articles(topic, title, content) AS (
    VALUES
    ('account', 'Tài khoản, Gmail và đăng nhập',
     'Đăng ký khách hàng bắt đầu bằng Gmail và mã xác thực 6 chữ số. Xác thực email xong mới nhập hồ sơ và mật khẩu. Mã xác thực và mật khẩu chỉ nhập vào màn hình của ứng dụng, không gửi trong chat. Dữ liệu hồ sơ gắn với tài khoản đang đăng nhập.'),
    ('catalog', 'Chọn dịch vụ, gói và diện tích dọn dẹp',
     'Chọn dịch vụ đang hoạt động trong Danh mục dịch vụ. Gói, tiện ích và câu hỏi bắt buộc tùy dịch vụ. Với tổng vệ sinh, hỏi tổng diện tích cần làm, loại nhà và phạm vi nếu chưa rõ. Gói theo diện tích phải tra định mức Catalog; phần đề xuất chưa bao gồm tiện ích hay ưu đãi và chưa xác nhận lịch.'),
    ('booking', 'Đặt lịch, chọn nhân viên và theo dõi đơn',
     'Khách chọn dịch vụ, gói, địa chỉ, ngày giờ, trả lời yêu cầu và xem lại giá trước khi xác nhận trên ứng dụng. Có thể chọn nhân viên cụ thể hoặc để hệ thống tìm người phù hợp tùy luồng đặt. Mục Đơn hàng cho phép mở chi tiết để xem tiến độ, lịch và trạng thái. Lịch cần kiểm tra theo địa chỉ, thời gian và dịch vụ cụ thể.'),
    ('address', 'Địa chỉ giao dịch và nơi thực hiện dịch vụ',
     'Địa chỉ đã lưu thuộc tài khoản khách. Khách chọn địa chỉ thực hiện và có thể đổi địa chỉ mặc định trong phần tài khoản. Với địa chỉ TP.HCM, chọn đúng phường hoặc xã từ danh sách của ứng dụng; kiểm tra số nhà, đường và thông tin căn hộ trước khi đặt.'),
    ('promotion', 'Voucher, ưu đãi và báo giá',
     'Mã giảm giá có thể phụ thuộc dịch vụ, thời hạn, số lượt và giá trị tối thiểu. Ưu đãi riêng gắn với tài khoản. Ứng dụng chỉ xác nhận mã đã áp dụng sau khi kiểm tra báo giá. Mức giá cuối và tiền giảm lấy từ bước xem trước, sau khi đã chọn gói và nhập đủ yêu cầu.'),
    ('payment', 'Thanh toán, hoàn tiền và chứng từ',
     'Mở chi tiết đơn để xem số tiền, phương thức và trạng thái thanh toán thực tế. Thực hiện theo phương thức mà ứng dụng hiển thị cho đơn đó. Nếu đã thanh toán mà trạng thái chưa cập nhật, kiểm tra lại chi tiết đơn và gửi yêu cầu hỗ trợ kèm mã đơn, không gửi thông tin bí mật trong chat. Phí hủy hoặc khoản hoàn cụ thể cần đối chiếu theo đơn và chính sách áp dụng.'),
    ('support', 'Khiếu nại, hư hại, bảo hiểm và hỗ trợ',
     'Nếu phát hiện đồ đạc hư hại, chất lượng dịch vụ chưa đạt hoặc có sự cố với nhân viên, mở chi tiết đơn của bạn để gửi khiếu nại và theo dõi lịch sử phản ánh. Mô tả sự cố, thời điểm và lưu bằng chứng nếu có. Việc gửi phản ánh chưa xác nhận trách nhiệm hay bồi thường. Khi chưa có chính sách bảo hiểm hoặc mức bồi thường đã công bố cho trường hợp đó, hướng dẫn liên hệ hỗ trợ qua ứng dụng.'),
    ('cancellation', 'Hủy đơn, đổi lịch và nhân viên chưa nhận',
     'Mở chi tiết đơn để xem các thao tác còn khả dụng vì quyền hủy, đổi hoặc tìm người khác phụ thuộc trạng thái đơn. Nếu lời mời nhân viên hết hạn, ứng dụng có thể cho gia hạn, tìm lại hoặc hủy tùy tình trạng. Kiểm tra trạng thái sau khi xác nhận. Không hứa hoàn tiền, thời hạn hoặc khoản phí nếu chưa có dữ liệu áp dụng cho đơn.')
), inserted AS (
    INSERT INTO knowledge_documents(title, source_uri, content)
    SELECT title, 'cleanmaster:customer-guide/' || topic, content FROM articles
    RETURNING id, content
)
INSERT INTO knowledge_chunks(document_id, chunk_index, content)
SELECT id, 0, content FROM inserted;
