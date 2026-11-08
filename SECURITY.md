# Security

## Báo cáo lỗ hổng

Vui lòng **không** mở issue công khai cho lỗ hổng bảo mật. Hãy dùng *Security → Report a vulnerability* trên GitHub của repo, hoặc liên hệ trực tiếp chủ repo.

## Quản lý bí mật

- Mật khẩu DB, SMTP, `GEMINI_API_KEY`, keystore chỉ đặt trong biến môi trường hoặc file `.env` (đã gitignore). Không bao giờ hard-code trong source, README hay script.
- Nếu lỡ commit một bí mật: **thu hồi/đổi nó ngay** (xoá khỏi git không đủ, vì lịch sử và bản fork vẫn giữ), sau đó mới dọn lịch sử nếu cần.
- Chạy `git log -p | grep -E "AIza|password"` trước khi public repo để rà soát.
- Tài khoản trong `database/seed-demo.sql` chỉ dành cho máy local.
