-- Schema đầy đủ cho database mới. Dữ liệu mẫu nằm ở database/seed-demo.sql (chỉ dùng cho demo local).
CREATE DATABASE IF NOT EXISTS online_auction
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE online_auction;

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at DATETIME DEFAULT NULL,
    password VARCHAR(255) NOT NULL,
    fullname VARCHAR(100) NOT NULL,
    role ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER',
    legit_points DECIMAL(8,2) NOT NULL DEFAULT 100.00,
    banned_until DATETIME DEFAULT NULL,
    unpaid_strike_count INT NOT NULL DEFAULT 0,
    paid_streak_count INT NOT NULL DEFAULT 0,
    phone VARCHAR(30) NULL,
    address VARCHAR(255) NULL,
    city VARCHAR(80) NULL,
    district VARCHAR(80) NULL,
    ward VARCHAR(80) NULL,
    citizen_id VARCHAR(30) NULL,
    gender VARCHAR(20) NULL,
    birth_date VARCHAR(20) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    seller_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category ENUM('ELECTRONICS', 'ART', 'VEHICLE', 'OTHER') NOT NULL DEFAULT 'OTHER',
    starting_price DECIMAL(15, 2) NOT NULL,
    min_increment DECIMAL(15, 2) NOT NULL,
    current_price DECIMAL(15, 2) NOT NULL,
    image_path VARCHAR(1000) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS auction_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    item_id INT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') DEFAULT 'OPEN',
    checkout_status ENUM('PENDING', 'PAID', 'CANCELED') DEFAULT NULL,
    payment_due_at DATETIME DEFAULT NULL,
    highlighted_until DATETIME DEFAULT NULL,
    current_highest_bid DECIMAL(15, 2) DEFAULT 0.00,
    bin_price DECIMAL(15, 2) DEFAULT NULL,
    winner_id INT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    FOREIGN KEY (winner_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_auction_status_end (status, end_time),
    INDEX idx_auction_status_start (status, start_time)
);

CREATE TABLE IF NOT EXISTS bids (
    id INT AUTO_INCREMENT PRIMARY KEY,
    auction_id INT NOT NULL,
    user_id INT NOT NULL,
    bid_amount DECIMAL(15, 2) NOT NULL,
    bid_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auction_id) REFERENCES auction_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_bids_auction_amount (auction_id, bid_amount)
);

CREATE TABLE IF NOT EXISTS auto_bids (
    id INT AUTO_INCREMENT PRIMARY KEY,
    auction_id INT NOT NULL,
    user_id INT NOT NULL,
    max_bid DECIMAL(15,2) NOT NULL,
    bid_increment DECIMAL(15,2) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auction_id) REFERENCES auction_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_autobids_auction_active (auction_id, is_active)
);

CREATE TABLE IF NOT EXISTS payment_profiles (
    user_id INT PRIMARY KEY,
    bank_account_number VARCHAR(40),
    bank_name VARCHAR(120),
    card_expiry VARCHAR(20),
    account_owner_name VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS verification_codes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    type ENUM('EMAIL', 'PASSWORD_RESET') NOT NULL,
    target VARCHAR(160) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_verification_lookup (user_id, type, target, used, expires_at),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    title VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'INFO',
    reference_id INT DEFAULT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notifications_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS cart_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    auction_id INT NOT NULL UNIQUE,
    item_id INT NOT NULL,
    bidder_id INT NOT NULL,
    winning_price DECIMAL(15, 2) NOT NULL,
    status ENUM('PENDING', 'PAID', 'CANCELED') DEFAULT 'PENDING',
    payment_method VARCHAR(80),
    shipping_address TEXT,
    shipping_phone VARCHAR(30),
    delivery_status ENUM('WAITING_PAYMENT', 'WAITING_SHIPMENT', 'SHIPPING', 'DELIVERED') NOT NULL DEFAULT 'WAITING_PAYMENT',
    tracking_code VARCHAR(120),
    won_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    payment_due_at DATETIME NOT NULL,
    paid_at DATETIME DEFAULT NULL,
    shipped_at DATETIME DEFAULT NULL,
    delivered_at DATETIME DEFAULT NULL,
    FOREIGN KEY (auction_id) REFERENCES auction_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_cart_status_due (status, payment_due_at)
);

CREATE TABLE IF NOT EXISTS auction_participants (
    auction_id INT NOT NULL,
    user_id INT NOT NULL,
    room_role ENUM('SELLER', 'BIDDER') NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (auction_id, user_id),
    FOREIGN KEY (auction_id) REFERENCES auction_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
