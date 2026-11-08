# BidShift - Online Auction System

Ứng dụng đấu giá trực tuyến viết bằng Java. Gồm JavaFX client, socket server, REST API nhỏ (xác thực phụ trợ, AI chat) và MySQL.

## Video Demo
https://drive.google.com/file/d/1V2arADcDjkDwKBHprlKgSmqmkPVy7TsK/view?usp=sharing

## Công nghệ

Java 17 · JavaFX · MySQL (HikariCP) · Socket TCP · REST (`com.sun.net.httpserver`) · Maven · Gson · SLF4J

## Cấu trúc mã nguồn

```
src/main/java/com/auction
├── Main.java                 điểm vào của jar: `server` hoặc `client`
├── client/                   JavaFX client
│   ├── controller/           màn hình (Login, Register, Dashboard, AuctionRoom, Admin)
│   ├── service/ServerConnector   kết nối socket + REST tới server
│   ├── navigation/ ui/ util/     điều hướng scene, widget, tiện ích (Async chạy việc mạng ngoài UI thread)
├── server/
│   ├── ServerMain.java       khởi động: migrate DB, scheduler, REST, socket server
│   ├── core/                 ClientHandler (kết nối), ClientManager (broadcast), AuctionScheduler,
│   │                         AuctionFinalizer, AutoBidManager
│   ├── handler/              ActionRouter + các nhóm action: Auth, Item, Auction, Bid, Account, Order, Admin
│   ├── dao/                  truy cập MySQL (DatabaseConnection là connection pool)
│   ├── auth/ security/       phiên đăng nhập, OTP, băm mật khẩu, giới hạn tần suất
│   ├── rest/ email/          REST API, gửi email OTP
└── shared/                   model, DTO, giao thức (Request/Response/ServerMessage), cấu hình, validator
database/                     init.sql (schema), migrate.sql (idempotent), seed-demo.sql (chỉ demo local)
```

Thêm một chức năng server mới = viết một method trong nhóm `*Actions` tương ứng và đăng ký action trong `register(router)`.

## Build và kiểm thử

```powershell
mvn clean package      # tạo target/online-auction.jar
mvn test               # unit test + JavaFX smoke test (test MySQL tự bỏ qua nếu chưa bật)
```

File jar **không** được commit vào git. Lấy bản build từ `target/`, từ artifact của GitHub Actions, hoặc từ GitHub Releases.

### Test với MySQL thật

Một số test chạy trên MySQL thật (BIN, anti-sniping, chốt phiên, đặt giá đồng thời). Hãy dùng một schema riêng, **không** trỏ vào database đang chạy thật:

```powershell
mysql -u root -p -e "CREATE DATABASE online_auction_test"
(Get-Content database\init.sql) -replace 'online_auction','online_auction_test' | mysql -u root -p
$env:AUCTION_DB_URL = "jdbc:mysql://localhost:3306/online_auction_test"
$env:AUCTION_DB_TESTS = "true"
mvn test
```

GitHub Actions tự dựng MySQL và chạy toàn bộ các test này cho mỗi push/PR.

### JavaFX visual snapshot

```powershell
mvn "-Dtest=JavaFxVisualSnapshotTest" "-Dauction.ui.snapshots=true" test
```

Ảnh được xuất vào `target/ui-snapshots` (kích thước `1366 x 768`).

## Cấu hình runtime

Đặt bằng biến môi trường, system property (`-Dkey=value`), hoặc file `.env` ở thư mục gốc (đã được gitignore, **không commit**).

| Biến môi trường | Ý nghĩa |
|---|---|
| `AUCTION_DB_URL`, `AUCTION_DB_USER` | Kết nối MySQL (mặc định `jdbc:mysql://localhost:3306/online_auction`, user `root`) |
| `AUCTION_DB_PASSWORD` | **Bắt buộc**, không có mật khẩu mặc định trong source |
| `AUCTION_DB_POOL_SIZE` | Số kết nối tối đa trong pool (mặc định 20) |
| `AUCTION_DB_AUTO_MIGRATE` | Mặc định `true`: tự chạy `database/migrate.sql` khi server khởi động |
| `AUCTION_SERVER_PORT`, `AUCTION_REST_PORT` | Cổng socket (8080) và REST (8081) |
| `AUCTION_SERVER_MAX_CLIENTS` | Số kết nối socket đồng thời tối đa (mặc định 500) |
| `AUCTION_SMTP_HOST/USER/PASSWORD/FROM` (+ `_PORT`, `_STARTTLS`, `_SSL`) | Gửi OTP qua email |
| `AUCTION_EMAIL_MOCK_CONSOLE` | Mặc định `false`. Đặt `true` **chỉ khi demo local** để in OTP ra console |
| `GEMINI_API_KEY` | Bật Trợ lý AI (tùy chọn). Thiếu thì AI tự tắt, không ảnh hưởng đấu giá |
| `AUCTION_TLS_ENABLED` | `true` để bật TLS cho cả socket và REST (xem bên dưới) |

Client đọc `AUCTION_SERVER_HOST`, `AUCTION_SERVER_PORT`, `AUCTION_REST_PORT` (hoặc `-Dauction.server.host=...`).
`release/application.properties` chỉ là mẫu trỏ về `127.0.0.1`.

## Khởi tạo database

```powershell
mysql -u root -p < database\init.sql         # schema mới
mysql -u root -p < database\seed-demo.sql    # (tùy chọn) dữ liệu demo, CHỈ cho máy local
```

Server tự chạy `migrate.sql` (idempotent) mỗi lần khởi động và băm lại mọi mật khẩu plaintext còn sót từ dữ liệu cũ.
Tắt auto migration: `java "-Dauction.db.autoMigrate=false" -jar online-auction.jar server`.

## Chạy ứng dụng

BidShift dùng 2 cổng: `8080` (socket: đăng nhập, đấu giá, checkout, realtime) và `8081` (REST: reset mật khẩu, AI chat, trạng thái cấu hình).

```powershell
java -jar target\online-auction.jar server
java "-Dauction.server.host=127.0.0.1" -jar target\online-auction.jar client
```

Client ở máy khác: mở firewall TCP `8080`/`8081` trên máy server, rồi đặt `-Dauction.server.host=<IP LAN / IP công khai / DDNS>`.
Nếu client ở ngoài mạng, forward cả hai cổng trên router. Client không cần truy cập MySQL; chỉ server cần.

Kiểm tra trạng thái SMTP/Gemini/migration: `Invoke-RestMethod http://127.0.0.1:8081/api/config/status`

## Bảo mật

- Mật khẩu băm PBKDF2-HMAC-SHA256 (600.000 vòng); hash cũ yếu hơn được nâng cấp khi đăng nhập.
- Đăng nhập sai nhiều lần sẽ bị khoá tạm theo tài khoản và theo IP; đăng ký, reset mật khẩu, gửi OTP, AI chat đều có giới hạn tần suất.
- Request socket tối đa 256 KB/dòng, body REST tối đa 64 KB, kết nối idle quá 3 phút bị ngắt (client tự gửi `PING` và tự đăng nhập lại bằng token phiên khi reconnect).
- `/api/chat` yêu cầu token phiên (`Authorization: Bearer ...`) do server cấp khi đăng nhập.
- **Mặc định socket và REST KHÔNG mã hoá.** Khi chạy qua Internet hãy bật TLS:

  ```powershell
  # tạo keystore tự ký (thay bằng chứng chỉ thật khi triển khai nghiêm túc)
  keytool -genkeypair -alias bidshift -keyalg RSA -keysize 2048 -validity 365 -keystore server.p12 -storetype PKCS12
  java -Dauction.tls.enabled=true -Djavax.net.ssl.keyStore=server.p12 -Djavax.net.ssl.keyStorePassword=*** -jar online-auction.jar server
  # client cần tin cậy chứng chỉ đó
  java -Dauction.tls.enabled=true -Djavax.net.ssl.trustStore=server.p12 -Djavax.net.ssl.trustStorePassword=*** -jar online-auction.jar client
  ```

- Không commit `.env`, keystore, hay địa chỉ máy chủ thật. Xem [SECURITY.md](SECURITY.md).

## Tài khoản demo

Chỉ có sau khi nạp `database/seed-demo.sql` (local): `admin`, `seller1`, `bidder1`, mật khẩu `Demo@12345`.
Không nạp file này trên server đặt ở Internet.

## Chức năng chính

Đăng ký/đăng nhập · đăng bán và quản lý sản phẩm · đặt giá realtime · mua ngay (BIN) · auto-bid kiểu proxy · chống "chốt giá phút chót" (anti-sniping) · giỏ hàng, checkout, theo dõi giao hàng · điểm uy tín và khoá đấu giá khi quá hạn thanh toán · admin panel · scheduler tự chuyển trạng thái phiên.
