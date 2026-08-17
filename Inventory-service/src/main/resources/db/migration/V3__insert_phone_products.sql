-- V3__insert_phone_products.sql

-- Insert Phone Products
INSERT INTO products (name, price) VALUES
('iPhone 15 Pro Max', 1199.99),
('iPhone 15 Pro', 999.99),
('iPhone 15', 799.99),
('iPhone 14 Pro Max', 1099.99),
('iPhone 14', 699.99),
('Samsung Galaxy S24 Ultra', 1299.99),
('Samsung Galaxy S24 Plus', 999.99),
('Samsung Galaxy S24', 799.99),
('Samsung Galaxy S23', 699.99),
('Google Pixel 8 Pro', 999.99),
('Google Pixel 8', 699.99),
('OnePlus 12', 799.99),
('OnePlus 11', 649.99),
('Xiaomi 14 Pro', 899.99),
('Xiaomi 13', 599.99);

-- Insert Inventory for each product
INSERT INTO inventory (product_id, available_quantity, reserved_quantity) VALUES
(1, 50, 5),   -- iPhone 15 Pro Max
(2, 75, 10),  -- iPhone 15 Pro
(3, 100, 15), -- iPhone 15
(4, 30, 5),   -- iPhone 14 Pro Max
(5, 80, 10),  -- iPhone 14
(6, 40, 8),   -- Samsung Galaxy S24 Ultra
(7, 60, 12),  -- Samsung Galaxy S24 Plus
(8, 90, 15),  -- Samsung Galaxy S24
(9, 70, 10),  -- Samsung Galaxy S23
(10, 45, 7),  -- Google Pixel 8 Pro
(11, 65, 10), -- Google Pixel 8
(12, 55, 8),  -- OnePlus 12
(13, 75, 12), -- OnePlus 11
(14, 35, 5),  -- Xiaomi 14 Pro
(15, 85, 10); -- Xiaomi 13
