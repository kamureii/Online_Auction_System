CREATE DATABASE IF NOT EXISTS online_auction;
USE online_auction;

CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    fullname VARCHAR(100) NOT NULL,
    role ENUM('ADMIN', 'SELLER', 'BIDDER') NOT NULL DEFAULT 'BIDDER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    seller_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    starting_price DECIMAL(15, 2) NOT NULL, -- Dùng DECIMAL cho tiền tệ để tránh sai số
    min_increment DECIMAL(15, 2) NOT NULL,
    current_price DECIMAL(15, 2) DEFAULT starting_price,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time DATETIME DEFAULT NULL,
    FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE auction_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    item_id INT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status ENUM('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELED') DEFAULT 'PENDING',
    current_highest_bid DECIMAL(15, 2) DEFAULT 0.00, -- Lưu sẵn giá cao nhất để truy vấn cho nhanh
    winner_id INT DEFAULT NULL, -- Người chiến thắng (Cập nhật khi phiên kết thúc)
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    FOREIGN KEY (winner_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE bids (
    id INT AUTO_INCREMENT PRIMARY KEY,
    auction_id INT NOT NULL,
    user_id INT NOT NULL,
    bid_amount DECIMAL(15, 2) NOT NULL,
    bid_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auction_id) REFERENCES auction_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

INSERT INTO users (username, password, fullname) VALUES ('admin', '123456', 'Người Quản Trị');
INSERT INTO items (name, starting_price, min_increment) VALUES ('iPhone 15 Pro', 1000, 50);