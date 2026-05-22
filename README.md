# BidShift - Online Auction System

## Mô tả
Ứng dụng đấu giá trực tuyến sử dụng JavaFX client, socket server, REST API và MySQL.

## Công nghệ sử dụng
- Java 17
- JavaFX
- MySQL
- Socket TCP
- REST API
- Maven
- Gson

## Cài đặt database
mysql -u root -p < database/init.sql

## Cấu hình môi trường
Tạo file .env:
AUCTION_DB_URL=jdbc:mysql://localhost:3306/online_auction
AUCTION_DB_USER=root
AUCTION_DB_PASSWORD=Kamurei2911

## Build
mvn clean package

## Chạy server
java -jar target/online-auction.jar server

## Chạy client
java -jar target/online-auction.jar client

## Tài khoản demo
admin / 123456
seller1 / 123456
bidder1 / 123456

## Chức năng đã hoàn thành
- Đăng ký, đăng nhập
- Quản lý sản phẩm đấu giá
- Đặt giá realtime
- Auto-bid
- Giỏ hàng/checkout
- Admin panel
- Scheduler tự động cập nhật trạng thái phiên
