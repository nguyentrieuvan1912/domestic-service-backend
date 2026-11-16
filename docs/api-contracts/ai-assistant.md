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

## Gợi ý dịch vụ và gói theo ngữ nghĩa

`POST /api/v1/ai-assistant/recommendations` dùng Bearer CUSTOMER, nhận:

```json
{"query":"Nhà lâu ngày chưa dọn, cần làm sạch kỹ trước Tết","areaM2":70,"maxStaffCount":2,"maxDurationMinutes":240}
```

`query` bắt buộc, tối đa 2.000 ký tự. Các giới hạn còn lại tùy chọn. Nếu query nêu rõ diện tích theo m² hoặc giới hạn thời lượng/số nhân viên, backend đọc các giá trị đó khi trường tương ứng chưa được gửi. Giới hạn tối đa là 100.000 m², 20 nhân viên, 720 phút.

Phản hồi có `status`, `embeddingModel`, `message`, `areaM2` và tối đa ba `candidates`. Trạng thái gồm RECOMMENDED, CLARIFY, NEEDS_DETAILS, NO_FIT và CATALOG_CHANGED. Mỗi candidate có serviceId, name, serviceType, similarity, score, reason, areaBased, needsArea, packages, sourceHash và embeddingId. Mỗi gói có packageId, name, quantity, staffCount, durationMinutes, similarity, reason, sourceHash và embeddingId.

Vector dùng model embedding local, lưu bất biến theo loại đối tượng/ID/hash nội dung/model trong `catalog_semantic_vectors` (V8). Worker làm ấm chỉ mục sau khởi động và kiểm tra lại mỗi phút. Request cũng bổ sung vector còn thiếu. Trước xếp hạng, dịch vụ/gói được lấy lại qua Catalog và lọc các giới hạn. Dịch vụ có định mức diện tích dùng API recommendation của Catalog. Trước trả kết quả, backend kiểm tra lại nội dung và điều kiện của các gói để tránh trả snapshot cũ trong lúc admin sửa danh mục.

Xếp hạng kết hợp cosine và mức khớp từ khóa tên/nhóm. Các kết quả quá yếu hoặc thấp hơn nhiều so với kết quả đầu được bỏ qua. Các dịch vụ cùng serviceType được gộp để danh sách không chỉ chứa các biến thể gần giống nhau. Nhu cầu chưa rõ được hỏi lại. Khi thiếu diện tích cho dịch vụ có định mức, AI đề nghị bổ sung trước khi đưa nút chọn gói.

Tool `recommend_services` dùng cùng luồng này. Chat lưu các thẻ gợi ý vào `ai_messages.suggestions` với sourceHash, embeddingId và embeddingModel để đối chiếu nguồn. Lịch sử và lượt gửi lại trả nguyên các thẻ đã lưu. Thẻ SERVICE mở chi tiết dịch vụ. Thẻ PACKAGE có thêm serviceId, quantity, requiredStaffCount và answers đã biết. Mobile chọn gói qua PATCH bản nháp có kiểm tra version; sau đó khách dùng thẻ Xác nhận để hoàn tất luồng Booking thường.

Quantity là số gói, bao gồm số gói combo. Gợi ý hiển thị tên gói, thời lượng và số nhân viên. Báo giá, lịch, nhân viên và thanh toán được kiểm tra ở bước đặt dịch vụ. Đề xuất gói theo ngữ nghĩa thuộc luồng tư vấn.

## Bản nháp đặt lịch

`GET /api/v1/ai-assistant/conversations/{id}/booking-draft` đọc bản nháp cập nhật gần nhất. Truyền `?draftId=...` để đọc đúng bản nháp được chọn. API trả về `id`, `version`, `expiresAt`, `data`, `missingSlots` và `choices` (dịch vụ, địa chỉ, gói, tiện ích, câu hỏi). Nếu không có bản nháp và không truyền ID, `id` và `version` là null. ID không thuộc hội thoại hoặc đã hết hạn trả 404. Chỉ chủ hội thoại với phiên CUSTOMER được truy cập.

`GET /api/v1/ai-assistant/conversations/{id}/booking-drafts` trả `{drafts, expandedDraftId}` để hiển thị các thẻ theo từng dịch vụ. Danh sách chỉ chứa bản nháp còn hạn đã có dịch vụ, với thông tin Catalog cho thẻ. Đọc chi tiết theo `draftId` để nạp cả lựa chọn địa chỉ. Khi lượt chat lưu bản nháp, `POST .../messages` trả thêm `bookingDraft` để mở thẻ tương ứng. Lượt chat không cập nhật bản nháp sẽ thu gọn các thẻ, với `expandedDraftId` là null. App đọc lại danh sách khi mở hội thoại hoặc khi phát lại lượt gửi.

Gọi `PATCH .../booking-draft` với `{"draftId":null,"version":null,"changes":{"serviceId":12}}` để lưu trường đầu tiên. Lượt sau gửi ID và version của bản nháp cập nhật gần nhất. Khi `changes.serviceId` chỉ sang dịch vụ khác, backend tạo bản nháp riêng hoặc cập nhật bản nháp còn hạn của dịch vụ đó. Dữ liệu các dịch vụ khác được giữ riêng. Giá trị null bỏ trường, còn `answers` gộp theo từng mã câu hỏi. Giờ bắt đầu dùng ISO local theo Asia/Ho_Chi_Minh, ví dụ `2026-12-15T14:00`. Bản nháp hết hạn sau 24 giờ từ lần sửa cuối. Nếu nhận 409, tải lại bản nháp trước khi sửa.

Khi khách yêu cầu đặt hoặc sửa bản nháp trong chat, Ollama có thể đề xuất các trường đã rõ. Backend đối chiếu với Catalog và Identity, chọn chế độ B (chờ nhân viên nhận) và lưu cùng tin nhắn trả lời. Vệ sinh một máy dùng gói giá thấp nhất dành cho một nhân viên; vệ sinh nhiều máy có thể chọn số nhân viên. Mobile mở đơn theo ID hội thoại và ID bản nháp trên thẻ, rồi điền các giá trị hợp lệ trong màn hình đặt dịch vụ để khách chọn lịch, địa chỉ và hoàn tất báo giá, thanh toán và đặt đơn. Cùng mã yêu cầu sẽ nhận lại lượt đã lưu. Nếu bản nháp đổi trong lúc Ollama xử lý, API trả 409 để tránh ghi đè.

## Giọng nói trong chat

`POST /api/v1/ai-assistant/voice/transcriptions` nhận `multipart/form-data` với trường `audio` và Bearer CUSTOMER. Định dạng nhận là M4A/MP4, WAV, WebM hoặc Ogg, tối đa 2 MiB mỗi bản thu. Ứng dụng dừng thu sau tối đa 30 giây, Whisper xử lý tối đa 30 giây đầu và trả `{ "text": "..." }` tiếng Việt. 422 dành cho tệp rỗng, định dạng không hỗ trợ hoặc bản thu không đọc được. 429 báo bộ chép lời đang bận và 503 báo dịch vụ chưa sẵn sàng.

Docker Compose chạy `whisper-voice` trong mạng nội bộ và tải model đa ngôn ngữ vào volume `whisper-model` ở lần chạy đầu. `WHISPER_VOICE_MODEL` mặc định là `large-v3-turbo-q5_0`, có thể dùng `small-q5_1`. `AI_TRANSCRIPTION_URL` là địa chỉ nội bộ cấu hình ở AI Service. Bản thu dùng tạm trong bộ nhớ cho quá trình chép lời, không lưu vào `ai_db`. Mobile xóa tệp tạm sau xử lý hoặc hủy. Kết quả chỉ điền vào ô nhập và đi theo luồng chat hiện có khi khách bấm gửi. Nút **Nghe** dùng giọng tiếng Việt của thiết bị và có thể dừng. Khách có thể tiếp tục nhập chữ nếu không cấp quyền micro hoặc thiết bị thiếu giọng đọc.

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
