# API hội thoại AI Assistant

Owner: Người 2. Consumer: Mobile/Web.

Các route dưới `/api/v1/ai-assistant/conversations` dùng Bearer token của CUSTOMER.

- GET và POST ở đường dẫn gốc để liệt kê và tạo hội thoại.
- GET `/{id}/messages` trả tin nhắn của hội thoại thuộc khách đang đăng nhập.
- POST `/{id}/messages` nhận `{content, clientKey}` và trả `{id, messages}` chứa các tin nhắn của lượt đã lưu. Gửi lại cùng mã và nội dung nhận lại kết quả đã lưu. Mã trùng với nội dung khác hoặc hội thoại đang xử lý một lượt trả 409.
- DELETE `/{id}` kết thúc hội thoại và xóa tin nhắn, nhật ký xử lý cùng dữ liệu nháp thuộc hội thoại. Đơn đã tạo được lưu riêng ở Booking Service.
- Ollama chưa chạy, thiếu model hoặc gặp lỗi thì lượt gửi trả 503. Có thể gửi lại cùng mã.

Ollama hỗ trợ các công cụ đọc danh mục, chi tiết và yêu cầu dịch vụ, báo giá xem trước, ưu đãi, địa chỉ, lịch nhân viên và trạng thái đơn. Các API cá nhân dùng phiên CUSTOMER đang gửi tin nhắn. Nhật ký `ai_tool_calls` lưu tên, trạng thái, mã HTTP và độ trễ cho lượt trả lời đã lưu. RAG và xác nhận tạo Booking thuộc các giai đoạn tiếp theo.

## Kiến thức khách hàng

Flyway V6 đưa các bài hướng dẫn khách hàng vào PostgreSQL. V7 bổ sung phiên bản, phê duyệt, lịch sử và trạng thái lập chỉ mục. Trợ lý truy xuất tối đa ba tài liệu đã duyệt đang hoạt động, kết hợp từ khóa và cosine trên embedding local. Khi embedding chưa sẵn sàng, phần truy xuất dùng từ khóa trên các đoạn thuộc phiên bản hiện tại. Nguồn trả lời hiển thị tên và phiên bản tài liệu. Giá, ưu đãi, lịch và dữ liệu theo tài khoản vẫn đi qua API nghiệp vụ.

Danh mục hiện tại được đọc qua Catalog ở từng lượt tư vấn. Tìm kiếm đối chiếu tên dịch vụ, tên nhóm và mô tả ngắn, hỗ trợ tiếng Việt không dấu và từ khóa trong câu hỏi tự nhiên. Công cụ `get_catalog_overview` đọc đủ các trang dịch vụ, nhóm và addon theo đúng dịch vụ sở hữu. Câu hỏi kiểm tra có dịch vụ hay không dùng kết quả Catalog, kể cả khi lịch sử chat từng trả lời sai. Lỗi nguồn dữ liệu trả thông báo kiểm tra thất bại để khách thử lại.

### Quản trị tài liệu RAG

Trang web `/admin/knowledge` dùng Bearer token ADMIN với các API dưới `/api/v1/ai-assistant/admin/knowledge`:

- GET đường dẫn gốc để xem danh sách và trạng thái lập chỉ mục.
- GET `/{id}` để đọc nội dung; GET `/{id}/history` để xem các phiên bản đã lưu.
- POST đường dẫn gốc nhận `{title,sourceUri,content,version:null}` tạo bản nháp.
- PUT `/{id}` nhận cùng dữ liệu và `version` hiện tại. Sửa nội dung đưa tài liệu về bản nháp, ngừng dùng bản cũ ngay. Phiên bản không khớp trả 409.
- POST `/{id}/approve` với `{version}` duyệt và đưa vào hàng chờ lập chỉ mục.
- POST `/{id}/hide` với `{version}` ẩn khỏi nguồn tra cứu.
- POST `/{id}/reindex` với `{version}` thử lại tài liệu bị lỗi. Tài liệu phải đang hoạt động và đã duyệt.
- POST `/search` với `{query}` trả `{mode,embeddingModel,articles}`. Mỗi đoạn có documentId, version, title, sourceUri, content, score và similarity. Mode HYBRID dùng embedding; KEYWORD hoặc KEYWORD_FALLBACK cho biết chỉ tìm từ khóa.

Tiêu đề tối đa 200 ký tự, nội dung 50.000 ký tự. Nguồn là URL http/https hoặc mã cleanmaster:, tối đa 500 ký tự. Nội dung hiển thị dạng văn bản. Chỉ tài liệu APPROVED và ACTIVE được dùng. Lịch sử giữ cả nội dung và admin thực hiện để đối chiếu.

Model embedding mặc định là `qwen3-embedding:0.6b`, tải vào cùng Ollama bằng `ollama pull qwen3-embedding:0.6b`. `OLLAMA_BASE_URL` là địa chỉ Ollama mà service truy cập. `OLLAMA_EMBEDDING_MODEL` có thể được đặt trong môi trường của tiến trình AI. Với quy mô hiện tại, vector được lưu JSONB trong PostgreSQL và tính cosine trong AI Service.

Worker chạy nền, chia đoạn 1.400 ký tự với chồng lấn 160 ký tự, gửi tối đa 8 đoạn mỗi lần tạo embedding. Trạng thái gồm PENDING, INDEXING, READY và FAILED. Gọi Ollama ngoài giao dịch database. Chỉ ghi kết quả khi version và mã tác vụ còn khớp; chỉnh sửa hoặc ẩn trong lúc chạy sẽ làm kết quả cũ mất hiệu lực. Tác vụ dở do khởi động lại được nhận lại sau lease 5 phút. Thất bại hiển thị ở admin để thử lại. `AI_KNOWLEDGE_INDEXER_ENABLED=false` tắt worker khi cần kiểm thử tách biệt.

## Bản nháp đặt lịch

`GET /api/v1/ai-assistant/conversations/{id}/booking-draft` trả về `id`, `version`, `expiresAt`, `data`, `missingSlots` và `choices` (thông tin dịch vụ, địa chỉ, gói, tiện ích, câu hỏi của dịch vụ). Bản nháp chưa có thì `id` và `version` là null. Chỉ chủ hội thoại với phiên CUSTOMER được truy cập.

Khi lượt chat lưu bản nháp, `POST .../messages` trả thêm `bookingDraft` cùng cấu trúc để app hiển thị thẻ ngay. Khi mở lại hội thoại hoặc nhận lượt gửi lại, app đọc bản nháp hiện tại qua GET.

Gọi `PATCH` cùng đường dẫn với `{"draftId":null,"version":null,"changes":{"serviceId":12}}` để lưu trường đầu tiên. Lượt sau gửi lại ID và version vừa nhận. Giá trị null bỏ trường, còn `answers` gộp theo từng mã câu hỏi. Đổi dịch vụ xóa gói, tiện ích, mã ưu đãi và câu trả lời của dịch vụ cũ. Giờ bắt đầu dùng ISO local theo Asia/Ho_Chi_Minh, ví dụ `2026-12-15T14:00`. Bản nháp hết hạn sau 24 giờ từ lần sửa cuối. Nếu nhận 409, tải lại bản nháp trước khi sửa.

Khi khách yêu cầu đặt hoặc sửa bản nháp trong chat, Ollama có thể đề xuất các trường đã rõ. Backend đối chiếu với Catalog và Identity, chọn chế độ B (chờ nhân viên nhận) và lưu cùng tin nhắn trả lời. Vệ sinh một máy dùng gói giá thấp nhất dành cho một nhân viên; vệ sinh nhiều máy có thể chọn số nhân viên. Mobile mở đơn nháp theo ID hội thoại và điền lại các giá trị hợp lệ trong màn hình đặt dịch vụ, nơi khách chọn lịch, địa chỉ và hoàn tất báo giá, thanh toán và đặt đơn. Cùng mã yêu cầu sẽ nhận lại lượt đã lưu. Nếu bản nháp đổi trong lúc Ollama xử lý, API trả 409 để tránh ghi đè.

## Chạy Ollama trên Windows

1. Cài [Ollama cho Windows](https://ollama.com/download/windows) và mở lại PowerShell. Ollama chạy nền trên cổng 11434. Nếu cổng chưa mở, khởi động Ollama từ Start.
2. Để tắt các tính năng cloud của Ollama, thêm thuộc tính sau vào `%USERPROFILE%\.ollama\server.json`. Giữ các thuộc tính đã có, sau đó thoát Ollama ở khay hệ thống và mở lại từ Start.

   ```json
   { "disable_ollama_cloud": true }
   ```

3. Tải model và kiểm tra API. Bước tải cần Internet. Bản Qwen3 8B mặc định chiếm khoảng 5,2 GB ổ đĩa.

   ```powershell
   ollama pull qwen3:8b
   ollama list
   Invoke-RestMethod http://127.0.0.1:11434/api/version
   ollama run qwen3:8b
   ```

   Trong cửa sổ chat của Ollama, thử một câu tiếng Việt, sau đó gõ `/bye` để quay lại PowerShell.

4. Trong `.env` của backend, đặt:

   ```dotenv
   OLLAMA_BASE_URL=http://host.docker.internal:11434
   OLLAMA_MODEL=qwen3:8b
   ```

   Nếu chạy Java trực tiếp trên Windows, dùng `http://127.0.0.1:11434`. Dùng địa chỉ Docker ở trên khi Java chạy trong container.

5. Tại thư mục backend, dựng lại riêng dịch vụ AI và kiểm tra health:

   ```powershell
   docker compose up -d --no-deps --build ai-assistant-service
   Invoke-RestMethod http://127.0.0.1:8085/actuator/health
   ```

6. Đăng nhập Customer trên app, mở AI và thử “Tìm giúp tôi các dịch vụ vệ sinh đang có” rồi “Tôi có bao nhiêu địa chỉ đã lưu?”. Đối chiếu với danh mục và hồ sơ của tài khoản. `ollama ps` cho biết tỷ lệ chạy CPU/GPU và context đang dùng. Nên gửi một lượt trước buổi demo để model được nạp sẵn.

Nếu Docker không truy cập được, kiểm tra từ container bằng `docker compose exec ai-assistant-service bash -c 'echo > /dev/tcp/host.docker.internal/11434'`. Ollama trên Windows mặc định chỉ lắng nghe ở loopback. Nếu phiên bản Docker Desktop của máy không chuyển tiếp được loopback, có thể dùng Ollama trong mạng Docker riêng với GPU hoặc cấu hình bind trên địa chỉ host mà Docker truy cập được. Giữ cổng API Ollama trong mạng nội bộ của máy demo.

Backend yêu cầu context 4096 token và tắt thinking để giảm độ trễ. File model và VRAM sử dụng khi chạy là hai số khác nhau. Nếu giả lập và model tranh GPU, có thể giảm tải ứng dụng GPU khác hoặc dùng `qwen3:4b` sau khi tải model, đổi `OLLAMA_MODEL` và tạo lại container AI.
