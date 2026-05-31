-- Update an existing online_auction database to the latest schema.
-- This file avoids legacy guarded ALTER syntax because older MySQL versions reject it.

USE online_auction;

-- items
SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'items'
      AND COLUMN_NAME = 'category'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE items ADD COLUMN category ENUM(''ELECTRONICS'', ''ART'', ''VEHICLE'', ''OTHER'') NOT NULL DEFAULT ''OTHER'' AFTER description',
    'SELECT ''items.category already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'items'
      AND COLUMN_NAME = 'image_path'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE items ADD COLUMN image_path VARCHAR(1000) NULL AFTER current_price',
    'SELECT ''items.image_path already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- users
SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'legit_points'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN legit_points DECIMAL(8,2) NOT NULL DEFAULT 100.00 AFTER role',
    'SELECT ''users.legit_points already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'banned_until'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN banned_until DATETIME DEFAULT NULL AFTER legit_points',
    'SELECT ''users.banned_until already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'unpaid_strike_count'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN unpaid_strike_count INT NOT NULL DEFAULT 0 AFTER banned_until',
    'SELECT ''users.unpaid_strike_count already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'paid_streak_count'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN paid_streak_count INT NOT NULL DEFAULT 0 AFTER unpaid_strike_count',
    'SELECT ''users.paid_streak_count already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'phone'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN phone VARCHAR(30) NULL AFTER paid_streak_count',
    'SELECT ''users.phone already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'email_verified'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE AFTER email',
    'SELECT ''users.email_verified already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'email_verified_at'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN email_verified_at DATETIME DEFAULT NULL AFTER email_verified',
    'SELECT ''users.email_verified_at already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'address'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN address VARCHAR(255) NULL AFTER phone',
    'SELECT ''users.address already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'city'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN city VARCHAR(80) NULL AFTER address',
    'SELECT ''users.city already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'district'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN district VARCHAR(80) NULL AFTER city',
    'SELECT ''users.district already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'ward'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN ward VARCHAR(80) NULL AFTER district',
    'SELECT ''users.ward already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'citizen_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN citizen_id VARCHAR(30) NULL AFTER ward',
    'SELECT ''users.citizen_id already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'gender'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN gender VARCHAR(20) NULL AFTER citizen_id',
    'SELECT ''users.gender already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'birth_date'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN birth_date VARCHAR(20) NULL AFTER gender',
    'SELECT ''users.birth_date already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

ALTER TABLE verification_codes
    MODIFY COLUMN type ENUM('EMAIL', 'PASSWORD_RESET') NOT NULL;

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

-- auction_sessions
ALTER TABLE auction_sessions MODIFY COLUMN status ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') DEFAULT 'OPEN';

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'auction_sessions'
      AND COLUMN_NAME = 'checkout_status'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE auction_sessions ADD COLUMN checkout_status ENUM(''PENDING'', ''PAID'', ''CANCELED'') DEFAULT NULL AFTER status',
    'SELECT ''auction_sessions.checkout_status already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'auction_sessions'
      AND COLUMN_NAME = 'payment_due_at'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE auction_sessions ADD COLUMN payment_due_at DATETIME DEFAULT NULL AFTER checkout_status',
    'SELECT ''auction_sessions.payment_due_at already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'auction_sessions'
      AND COLUMN_NAME = 'highlighted_until'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE auction_sessions ADD COLUMN highlighted_until DATETIME DEFAULT NULL AFTER payment_due_at',
    'SELECT ''auction_sessions.highlighted_until already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'auction_sessions'
      AND COLUMN_NAME = 'bin_price'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE auction_sessions ADD COLUMN bin_price DECIMAL(15, 2) DEFAULT NULL AFTER current_highest_bid',
    'SELECT ''auction_sessions.bin_price already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'auction_sessions'
      AND COLUMN_NAME = 'created_at'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE auction_sessions ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP',
    'SELECT ''auction_sessions.created_at already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND COLUMN_NAME = 'reference_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE notifications ADD COLUMN reference_id INT DEFAULT NULL AFTER type',
    'SELECT ''notifications.reference_id already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'cart_items'
      AND COLUMN_NAME = 'shipping_phone'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE cart_items ADD COLUMN shipping_phone VARCHAR(30) NULL AFTER shipping_address',
    'SELECT ''cart_items.shipping_phone already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'cart_items'
      AND COLUMN_NAME = 'delivery_status'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE cart_items ADD COLUMN delivery_status ENUM(''WAITING_PAYMENT'', ''WAITING_SHIPMENT'', ''SHIPPING'', ''DELIVERED'') NOT NULL DEFAULT ''WAITING_PAYMENT'' AFTER shipping_address',
    'SELECT ''cart_items.delivery_status already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'cart_items'
      AND COLUMN_NAME = 'tracking_code'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE cart_items ADD COLUMN tracking_code VARCHAR(120) NULL AFTER delivery_status',
    'SELECT ''cart_items.tracking_code already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'cart_items'
      AND COLUMN_NAME = 'shipped_at'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE cart_items ADD COLUMN shipped_at DATETIME DEFAULT NULL AFTER paid_at',
    'SELECT ''cart_items.shipped_at already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'cart_items'
      AND COLUMN_NAME = 'delivered_at'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE cart_items ADD COLUMN delivered_at DATETIME DEFAULT NULL AFTER shipped_at',
    'SELECT ''cart_items.delivered_at already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE cart_items
SET delivery_status = CASE
    WHEN status = 'PAID' AND delivery_status = 'WAITING_PAYMENT' THEN 'WAITING_SHIPMENT'
    ELSE delivery_status
END;

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
