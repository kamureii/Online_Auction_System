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
- `AUCTION_DB_AUTO_MIGRATE` / `auction.db.autoMigrate`, mặc định `true`, tự chạy `database/migrate.sql` khi server khởi động
- `AUCTION_SMTP_HOST`, `AUCTION_SMTP_USER`, `AUCTION_SMTP_PASSWORD`, `AUCTION_SMTP_FROM` để gửi OTP qua email
- `AUCTION_EMAIL_MOCK_CONSOLE=true` chỉ dùng cho demo local khi muốn in OTP ra console server
- `GEMINI_API_KEY` / `gemini.api.key` để bật Trợ lý AI; nếu thiếu, AI sẽ tự hiển thị trạng thái tắt và không ảnh hưởng đấu giá

File `.env` được hỗ trợ khi chạy local nhưng không được commit.

## Chạy ứng dụng

BidShift dùng 2 cổng:

- `8080`: socket server cho đăng nhập, đấu giá, checkout, realtime.
- `8081`: REST API cho xác thực phụ trợ và AI chat.

JAR nộp bài nằm ở `release/online-auction.jar`. Khi đang dev có thể thay bằng `target/online-auction.jar`.

Khi server khởi động, hệ thống mặc định tự chạy migration DB idempotent từ `database/migrate.sql`. Nếu muốn tắt auto migration để chạy thủ công:

```powershell
java "-Dauction.db.autoMigrate=false" -jar release\online-auction.jar server
```

Lệnh migration thủ công khi cần:

```powershell
mysql -h localhost -P 3306 -u root -p online_auction < database\migrate.sql
```

Có thể kiểm tra nhanh trạng thái SMTP/Gemini/auto migration qua REST sau khi server chạy:

```powershell
Invoke-RestMethod http://127.0.0.1:8081/api/config/status
```

### 1. Server và client cùng một máy

Mở terminal 1 để chạy server:

```powershell
java -jar release\online-auction.jar server
```

Mở terminal 2 để chạy client trên cùng máy:

```powershell
java "-Dauction.server.host=127.0.0.1" "-Dauction.server.port=8080" "-Dauction.rest.port=8081" -jar release\online-auction.jar client
```

### 2. Server và client khác máy nhưng cùng mạng LAN/Wi-Fi

Trên máy chạy server, lấy địa chỉ IPv4 LAN:

```powershell
ipconfig
```

Ví dụ máy server có IPv4 là `192.168.1.20`.

Trên máy server, cho phép firewall inbound TCP `8080` và `8081`, rồi chạy:

```powershell
java -jar release\online-auction.jar server
```

Trên máy client trong cùng mạng, chạy:

```powershell
java "-Dauction.server.host=192.168.1.20" "-Dauction.server.port=8080" "-Dauction.rest.port=8081" -jar release\online-auction.jar client
```

Client không cần kết nối MySQL trực tiếp; chỉ máy server cần DB.

### 3. Server và client khác mạng (port forwarding)

Trên router của mạng đặt máy server:

1. Gán IP LAN cố định cho máy server, ví dụ `192.168.1.20`.
2. Forward TCP `8080` từ WAN về `192.168.1.20:8080`.
3. Forward TCP `8081` từ WAN về `192.168.1.20:8081`.
4. Mở Windows Firewall trên máy server cho TCP `8080` và `8081`.
5. Lấy public IP hoặc cấu hình DDNS, ví dụ `203.0.113.10` hoặc `bidshift-demo.ddns.net`.

Trên máy server:

```powershell
java -jar release\online-auction.jar server
```

Trên máy client ở mạng khác:

```powershell
java "-Dauction.server.host=203.0.113.10" "-Dauction.server.port=8080" "-Dauction.rest.port=8081" -jar release\online-auction.jar client
```

Nếu dùng DDNS:

```powershell
java "-Dauction.server.host=bidshift-demo.ddns.net" "-Dauction.server.port=8080" "-Dauction.rest.port=8081" -jar release\online-auction.jar client
```

Nếu client không kết nối được, kiểm tra theo thứ tự: server còn đang chạy, public IP/DDNS đúng, router đã forward cả `8080` và `8081`, firewall đã mở, và nhà mạng không chặn inbound port.

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
