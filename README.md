# BidShift - Online Auction System

Ứng dụng đấu giá trực tuyến JavaFX + Socket/REST server + MySQL.

## Cách Chạy Nhanh

1. Tạo database MySQL bằng script:

```sql
SOURCE database/init.sql;
```

Nếu đã có database cũ, chạy thêm:

```sql
SOURCE database/migrate.sql;
```

2. Chạy server:

```powershell
mvn exec:java -Dexec.mainClass="com.auction.server.ServerMain"
```

3. Chạy client JavaFX:

```powershell
mvn javafx:run
```

`pom.xml` đã trỏ JavaFX main class về `com.auction.client.Launcher`.

## Cấu Hình Môi Trường

Mặc định ứng dụng dùng MySQL local `online_auction`, user `root`, password rỗng. Có thể đổi bằng system property, environment variable hoặc file `.env` ở thư mục gốc project:

```powershell
$env:AUCTION_DB_URL="jdbc:mysql://localhost:3306/online_auction"
$env:AUCTION_DB_USER="root"
$env:AUCTION_DB_PASSWORD=""
$env:AUCTION_SERVER_PORT="8080"
$env:AUCTION_REST_PORT="8081"
```

AI chat chỉ hoạt động khi server có Gemini key:

```powershell
$env:GEMINI_API_KEY="your-api-key"
$env:GEMINI_MODEL="gemini-2.5-flash"
```

Hoặc tạo file `.env` từ `.env.example` rồi điền key thật:

```dotenv
GEMINI_API_KEY=your-api-key
GEMINI_MODEL=gemini-2.5-flash
```

Email OTP sẽ gửi qua SMTP nếu có cấu hình:

```powershell
$env:AUCTION_SMTP_HOST="smtp.gmail.com"
$env:AUCTION_SMTP_PORT="587"
$env:AUCTION_SMTP_USER="your-email@gmail.com"
$env:AUCTION_SMTP_PASSWORD="your-app-password"
$env:AUCTION_SMTP_FROM="your-email@gmail.com"
```

Nếu chưa cấu hình SMTP, server sẽ in OTP trong console để phục vụ demo.

## Ghi Chú Nghiệp Vụ

- App mở vào trang chủ trước, login/register có nút quay về trang chủ.
- User phải xác thực email trước khi đăng bán, đặt giá, auto-bid hoặc checkout.
- Server tự động chạy `AuctionScheduler` để chuyển `OPEN -> RUNNING -> FINISHED`.
- Người bán không được tự bid sản phẩm của mình.
- Checkout thành công cộng điểm uy tín; quá hạn thanh toán bị trừ điểm và khóa đấu giá tạm thời.
