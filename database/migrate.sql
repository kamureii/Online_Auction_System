-- Script cập nhật database từ schema cũ sang schema mới
-- Chạy script này nếu đã có database online_auction từ trước

USE online_auction;

-- Thêm cột category vào items (nếu chưa có)
ALTER TABLE items ADD COLUMN IF NOT EXISTS category ENUM('ELECTRONICS', 'ART', 'VEHICLE', 'OTHER') NOT NULL DEFAULT 'OTHER' AFTER description;
ALTER TABLE items ADD COLUMN IF NOT EXISTS image_path VARCHAR(1000) NULL AFTER current_price;

ALTER TABLE users ADD COLUMN IF NOT EXISTS legit_points DECIMAL(8,2) NOT NULL DEFAULT 100.00 AFTER role;
ALTER TABLE users ADD COLUMN IF NOT EXISTS banned_until DATETIME DEFAULT NULL AFTER legit_points;
ALTER TABLE users ADD COLUMN IF NOT EXISTS unpaid_strike_count INT NOT NULL DEFAULT 0 AFTER banned_until;
ALTER TABLE users ADD COLUMN IF NOT EXISTS paid_streak_count INT NOT NULL DEFAULT 0 AFTER unpaid_strike_count;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30) NULL AFTER paid_streak_count;
ALTER TABLE users ADD COLUMN IF NOT EXISTS address VARCHAR(255) NULL AFTER phone;
ALTER TABLE users ADD COLUMN IF NOT EXISTS city VARCHAR(80) NULL AFTER address;
ALTER TABLE users ADD COLUMN IF NOT EXISTS district VARCHAR(80) NULL AFTER city;
ALTER TABLE users ADD COLUMN IF NOT EXISTS ward VARCHAR(80) NULL AFTER district;
ALTER TABLE users ADD COLUMN IF NOT EXISTS citizen_id VARCHAR(30) NULL AFTER ward;
ALTER TABLE users ADD COLUMN IF NOT EXISTS gender VARCHAR(20) NULL AFTER citizen_id;
ALTER TABLE users ADD COLUMN IF NOT EXISTS birth_date VARCHAR(20) NULL AFTER gender;

ALTER TABLE users MODIFY COLUMN role ENUM('ADMIN', 'SELLER', 'BIDDER', 'USER') NOT NULL DEFAULT 'USER';
UPDATE users SET role = 'USER' WHERE role IN ('SELLER', 'BIDDER');
ALTER TABLE users MODIFY COLUMN role ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER';

CREATE TABLE IF NOT EXISTS payment_profiles (
    user_id INT PRIMARY KEY,
    bank_account_number VARCHAR(40),
    bank_name VARCHAR(120),
    card_expiry VARCHAR(20),
    account_owner_name VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Thêm bảng auto_bids (nếu chưa có)
CREATE TABLE IF NOT EXISTS auto_bids (
    id INT AUTO_INCREMENT PRIMARY KEY,
    auction_id INT NOT NULL,
    user_id INT NOT NULL,
    max_bid DECIMAL(15,2) NOT NULL,
    bid_increment DECIMAL(15,2) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auction_id) REFERENCES auction_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Sửa enum status trong auction_sessions (nếu cần)
ALTER TABLE auction_sessions MODIFY COLUMN status ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') DEFAULT 'OPEN';
ALTER TABLE auction_sessions ADD COLUMN IF NOT EXISTS checkout_status ENUM('PENDING', 'PAID', 'CANCELED') DEFAULT NULL AFTER status;
ALTER TABLE auction_sessions ADD COLUMN IF NOT EXISTS payment_due_at DATETIME DEFAULT NULL AFTER checkout_status;
ALTER TABLE auction_sessions ADD COLUMN IF NOT EXISTS highlighted_until DATETIME DEFAULT NULL AFTER payment_due_at;

-- Thêm cột created_at vào auction_sessions (nếu chưa có)
ALTER TABLE auction_sessions ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    title VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'INFO',
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS reference_id INT DEFAULT NULL AFTER type;

CREATE TABLE IF NOT EXISTS cart_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    auction_id INT NOT NULL UNIQUE,
    item_id INT NOT NULL,
    bidder_id INT NOT NULL,
    winning_price DECIMAL(15, 2) NOT NULL,
    status ENUM('PENDING', 'PAID', 'CANCELED') DEFAULT 'PENDING',
    payment_method VARCHAR(80),
    shipping_address TEXT,
    won_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    payment_due_at DATETIME NOT NULL,
    paid_at DATETIME DEFAULT NULL,
    FOREIGN KEY (auction_id) REFERENCES auction_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id) REFERENCES users(id) ON DELETE CASCADE
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

INSERT INTO auction_participants (auction_id, user_id, room_role)
SELECT a.id, i.seller_id, 'SELLER'
FROM auction_sessions a
JOIN items i ON a.item_id = i.id
ON DUPLICATE KEY UPDATE room_role = VALUES(room_role);
