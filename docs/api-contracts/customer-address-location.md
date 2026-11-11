# Tọa độ địa chỉ khách hàng

POST /api/v1/identity/customer/addresses và PUT /api/v1/identity/customer/addresses/{id} nhận thêm latitude, longitude tùy chọn, phải có đủ một cặp. Vĩ độ nằm trong [-90, 90], kinh độ trong [-180, 180]. Quyền sở hữu lấy từ phiên đăng nhập như luồng địa chỉ hiện hành.

Tọa độ được lưu cùng địa chỉ trong một transaction và trả lại qua API đọc địa chỉ hiện có. Khách vẫn sửa tên người nhận, điện thoại, số nhà, phường/xã và ghi chú trước khi lưu.

Client cũ có thể tiếp tục gửi biểu mẫu không có tọa độ. Với cập nhật không kèm tọa độ, backend giữ ghim hiện có khi đường, phường/xã, quận và thành phố vẫn khớp. Khi đổi địa chỉ phục vụ mà không cung cấp vị trí mới, backend xóa tọa độ cũ để tránh gắn nhầm ghim.

Mobile chỉ xin quyền vị trí khi khách bấm Lấy vị trí hiện tại. Kết quả GPS và tra cứu địa chỉ điền vào biểu mẫu, khách bấm Lưu địa chỉ mới ghi vào database. Danh sách phường/xã và phạm vi phục vụ hiện tại tiếp tục lấy từ Identity.
