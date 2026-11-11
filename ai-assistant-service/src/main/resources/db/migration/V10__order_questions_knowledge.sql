WITH articles(topic, title, content) AS (
    VALUES
    ('order-draft', 'Bản nháp trong chat và đơn dịch vụ đã đặt',
     'Tôi đã nói muốn tìm bảo mẫu hoặc đi chợ hộ, đã có đơn chưa? Nếu chat hiện thẻ Đơn nháp, thông tin đã được lưu để tiếp tục. Bấm Xác nhận trên thẻ để mở màn hình đặt dịch vụ, bổ sung dữ liệu còn thiếu, kiểm tra lịch và báo giá rồi hoàn tất đặt dịch vụ. Kiểm tra mục Đơn hàng để xem đơn đã đặt. Mỗi dịch vụ có thẻ nháp riêng. Khi đổi chủ đề, bấm tên dịch vụ để mở lại thẻ đã thu gọn.'),
    ('order-progress', 'Mã đơn, lịch sử đặt và tiến độ nhân viên',
     'Tìm mã đơn ở đâu? Mở mục Đơn hàng rồi chọn đơn để xem mã, lịch và tiến độ. Có thể hỏi chat về mã đơn cụ thể hoặc đơn gần nhất. Đang tìm nhân viên và Chờ nhân viên nhận là hai trạng thái trước khi có người nhận. Đang thực hiện cho biết công việc đã bắt đầu. Xem trạng thái thực tế của đơn trong tài khoản đang đăng nhập, không suy ra từ ngày hẹn hoặc tin nhắn trước đó.'),
    ('order-payment-check', 'Kiểm tra thanh toán và xử lý khi thông tin đơn chưa cập nhật',
     'Tôi đã chuyển khoản mà ứng dụng còn báo chờ thanh toán thì sao? Mở chi tiết đúng đơn và kiểm tra trạng thái thanh toán. Nếu trạng thái vẫn chưa cập nhật, gửi yêu cầu hỗ trợ kèm mã đơn qua ứng dụng. Chỉ xác nhận đã thanh toán khi hệ thống ghi nhận trạng thái tương ứng. Hủy đơn và hoàn tiền có trạng thái riêng. Tra từng đơn để biết tình trạng, và dùng thao tác còn khả dụng trong chi tiết đơn để gửi yêu cầu.')
), inserted AS (
    INSERT INTO knowledge_documents(title, source_uri, content, content_hash, approval_status)
    SELECT title, 'cleanmaster:customer-guide/' || topic, content,
        encode(sha256(convert_to(content, 'UTF8')), 'hex'), 'APPROVED'
    FROM articles
    RETURNING id, version, title, source_uri, content, content_hash, status, approval_status, updated_at
), revisions AS (
    INSERT INTO knowledge_document_revisions(document_id, version, title, source_uri, content, content_hash, status, approval_status, created_at)
    SELECT id, version, title, source_uri, content, content_hash, status, approval_status, updated_at FROM inserted
)
INSERT INTO knowledge_chunks(document_id, document_version, chunk_index, content)
SELECT id, version, 0, content FROM inserted;
