# BidShift - Online Auction System

Ứng dụng đấu giá trực tuyến viết bằng Java. Project gồm JavaFX client, socket server, REST API nhỏ cho chat/xác thực và MySQL làm nơi lưu dữ liệu.

## Công nghệ

- Java 17
- JavaFX
- MySQL
- Socket TCP
- REST API
- Maven
- Gson

## Build

```powershell
mvn clean package
```

File JAR dùng để nộp bài nằm tại `release/online-auction.jar`.

## Cấu hình runtime

Server cần các biến môi trường hoặc system properties sau khi chạy ngoài IDE:

- `AUCTION_DB_URL` / `auction.db.url`
- `AUCTION_DB_USER` / `auction.db.user`
- `AUCTION_DB_PASSWORD` / `auction.db.password` bắt buộc, không có password mặc định trong source
- `AUCTION_SMTP_HOST`, `AUCTION_SMTP_USER`, `AUCTION_SMTP_PASSWORD`, `AUCTION_SMTP_FROM` để gửi OTP qua email
- `AUCTION_EMAIL_MOCK_CONSOLE=true` chỉ dùng cho demo local khi muốn in OTP ra console server

File `.env` được hỗ trợ khi chạy local nhưng không được commit.

## Chạy ứng dụng

Chạy server:

```powershell
java -jar target/online-auction.jar server
```

Chạy client:

```powershell
java "-Dauction.server.host=14.177.166.233" "-Dauction.server.port=8080" "-Dauction.rest.port=8081" -jar .\target\online-auction.jar client
```

## Tài khoản demo

- `admin / 123456`
- `seller1 / 123456`
- `bidder1 / 123456`

## Chức năng chính

- Đăng ký, đăng nhập
- Quản lý sản phẩm đấu giá
- Đặt giá realtime
- Auto-bid
- Giỏ hàng và checkout
- Admin panel
- Scheduler tự động cập nhật trạng thái phiên đấu giá
