-- Test script to check if the items table exists and has the correct structure
-- Run this in your MySQL database to verify the setup

DESCRIBE items;

-- If the table doesn't exist, create it with this structure:
CREATE TABLE IF NOT EXISTS items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    starting_price DECIMAL(10,2) NOT NULL,
    current_price DECIMAL(10,2) NOT NULL,
    minimum_step INT NOT NULL,
    owner_id INT NOT NULL,
    end_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Test insert to verify permissions:
INSERT INTO items (name, description, starting_price, current_price, minimum_step, owner_id, end_time) 
VALUES ('Test Item', 'Test Description', 100000.00, 100000.00, 10000, 1, DATE_ADD(NOW(), INTERVAL 1 DAY));

-- Check if the test item was added:
SELECT * FROM items WHERE name = 'Test Item';

-- Clean up test data:
DELETE FROM items WHERE name = 'Test Item';