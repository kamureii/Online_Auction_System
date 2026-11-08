-- DỮ LIỆU DEMO - CHỈ DÙNG TRÊN MÁY LOCAL.
-- Ba tài khoản admin/seller1/bidder1 dùng chung mật khẩu 'Demo@12345' (đã băm PBKDF2).
-- KHÔNG nạp file này vào server đặt trên Internet; hoặc đổi mật khẩu ngay sau khi nạp.
USE online_auction;

INSERT INTO users (username, email, email_verified, email_verified_at, password, fullname, role)
VALUES
    ('admin', 'admin@auction.com', TRUE, NOW(), 'pbkdf2$600000$n0uBTfgMf7SE/hbv0o4d8Q==$JcHWtmV+j3CrFziP5+P4T7nmOS75F9MJK8CpKuDwiqg=', 'Quan Tri Vien', 'ADMIN'),
    ('seller1', 'seller1@auction.com', TRUE, NOW(), 'pbkdf2$600000$9VofHRsULOP1pAE6td/kBg==$oH+Qxnet+IzlBQ2SNl6Mize1qrn4UkW2dieu9KQHB1I=', 'Nguoi Ban 1', 'USER'),
    ('bidder1', 'bidder1@auction.com', TRUE, NOW(), 'pbkdf2$600000$n9SmyG8KH9OMNHEzBHQyzg==$i9TBRAfwWJ3GKTXh/JBvG4cq6JIqm5vWCjKH0qPr6GA=', 'Nguoi Mua 1', 'USER')
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO items (id, seller_id, name, description, category, starting_price, min_increment, current_price)
VALUES
    (1, 2, 'iPhone 15 Pro Max', 'Dien thoai Apple iPhone 15 Pro Max 256GB', 'ELECTRONICS', 25000000, 500000, 25000000),
    (2, 2, 'Tranh Son Dau Phong Canh', 'Tranh son dau ve tay phong canh Viet Nam', 'ART', 5000000, 200000, 5000000),
    (3, 2, 'Honda Civic 2024', 'Xe Honda Civic RS 2024 moi 100%', 'VEHICLE', 800000000, 5000000, 800000000)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO auction_sessions (id, item_id, start_time, end_time, status, current_highest_bid, bin_price)
VALUES
    (1, 1, NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR), 'RUNNING', 25000000, 30000000),
    (2, 2, NOW(), DATE_ADD(NOW(), INTERVAL 48 HOUR), 'RUNNING', 5000000, 8000000),
    (3, 3, NOW(), DATE_ADD(NOW(), INTERVAL 72 HOUR), 'RUNNING', 800000000, 900000000)
ON DUPLICATE KEY UPDATE current_highest_bid = VALUES(current_highest_bid), bin_price = VALUES(bin_price);

INSERT INTO auction_participants (auction_id, user_id, room_role)
SELECT a.id, i.seller_id, 'SELLER'
FROM auction_sessions a
JOIN items i ON a.item_id = i.id
ON DUPLICATE KEY UPDATE room_role = VALUES(room_role);
