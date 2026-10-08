# Quy trình Git cho 2 người

Một repo backend, main là nhánh tích hợp chạy được. Mỗi người có nhiều nhánh feature ngắn,
không giữ hai nhánh person1/person2 sống dài vì sẽ khó đồng bộ.

- Người 1: feat/p1-identity-login, feat/p1-catalog-quote, feat/p1-finance-payment.
- Người 2: feat/p2-booking-preview, feat/p2-staff-availability, feat/p2-ai-draft.
- PR nhỏ; người còn lại review; merge sau khi CI pass.
- Không push main trực tiếp; bật branch protection/require checks trong GitHub khi cấu hình repo.

## Nhận cấu hình ban đầu

Người 1 commit/push scaffold trước. Người 2 clone main mới nhất rồi tạo feature branch.
Không chạy Spring Initializr lại, không tạo Git repo riêng cho từng service.

## Trước khi bắt đầu việc mới

```powershell
git switch main
git pull --ff-only origin main
git switch -c feat/p2-booking-preview
```

## Trước khi gửi PR

Commit thay đổi trước khi rebase (hoặc stash nếu còn việc dở):

```powershell
git fetch origin
git rebase origin/main
.\mvnw.cmd -B --no-transfer-progress verify
git push -u origin feat/p2-booking-preview
```

Nếu rebase có conflict: xem git status, xử lý từng file, git add file đã sửa rồi git rebase --continue.
Nếu chưa chắc cách giải quyết, dùng git rebase --abort và phối hợp owner.
Nếu nhánh feature riêng đã push trước rebase, chỉ dùng --force-with-lease khi biết không ai khác
đang làm trên nhánh đó; không dùng cho main hay nhánh chung.

## Giảm conflict

| File/khu vực | Quyền sửa chính |
|---|---|
| identity-service, catalog-service, finance-service | Người 1 |
| booking-service, ai-assistant-service | Người 2 |
| pom.xml root, common-web, api-gateway, compose.yml, .github, scripts | Người 1 |
| docs/api-contracts/{service}.md | Owner của service |
| docs/api-contracts/README.md, TEAM_ASSIGNMENT.md | Chốt cùng nhau, Người 1 cập nhật |

Thêm dependency riêng trong pom.xml của service, không đẩy lên parent nếu chỉ một service cần.
Không chạy format/bulk rename trên service của người kia.
Tạo migration trong thư mục của service; không sửa file migration đã áp dụng.
Tách PR cấu hình chung khỏi PR nghiệp vụ nếu có thể.
Không commit .env, target, IDE files, database dump hay log.
API thay đổi phải cập nhật contract và thông báo bên gọi; không đổi DTO âm thầm.

## Tài liệu chung và hướng dẫn cá nhân

Chỉ cập nhật docs/ khi thay đổi ảnh hưởng cả hai người: cấu hình chạy chung, API contract
mà service của người kia sử dụng, schema/enum ảnh hưởng tích hợp, phân công và hướng dẫn handoff.
Các tài liệu này được đưa vào cùng commit/PR liên quan.

Chi tiết triển khai riêng, ghi chú debug và hướng dẫn chạy/test thủ công của Người 1
lưu trong .local-notes/person1/. Thư mục này được Git ignore và loại khỏi Docker build context;
không dùng git add -f để đưa vào repo.

Code nghiệp vụ và migration vẫn nằm trong service tương ứng để đồng bộ theo quy trình Git.
Việc không đưa hướng dẫn cá nhân lên Git không có nghĩa đưa source code ra ngoài repo.
Không cần sửa docs chung cho một thay đổi nội bộ không ảnh hưởng người kia.
Trước khi commit, kiểm tra git diff và git diff --cached; không tự commit/push chỉ vì docs được phép chia sẻ.

Workflow giảm phạm vi chồng chéo, không thể bảo đảm tuyệt đối không có conflict.
File CI không tự tạo branch protection hay quyền repo; thao tác đó thực hiện trên GitHub Settings.
