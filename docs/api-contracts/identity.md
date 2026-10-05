# identity API contract — draft

Owner: Người 1. Consumer: mọi service.
Dự thảo: POST /api/v1/identity/auth/register, /login, /refresh, /logout;
GET/PATCH /api/v1/identity/accounts/me; quản lý address Customer.
Cần chốt JWT issuer/audience/claims/roles, refresh rotation, account lookup nội bộ,
trạng thái locked/inactive và cách trả lỗi 401/403.
Identity không sở hữu lịch rảnh, năng lực hay earning của Staff.

Đã triển khai: GET /api/v1/identity/system/info trả {service, stage: "scaffold"}.
