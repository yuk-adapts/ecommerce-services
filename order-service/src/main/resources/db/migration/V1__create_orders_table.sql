-- Create orders table
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    status ENUM('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED') NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    payment_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Create index on user_id for faster queries
CREATE INDEX idx_orders_user_id ON orders(user_id);

-- Create index on status for filtering
CREATE INDEX idx_orders_status ON orders(status);

-- Create composite index for user_id and status
CREATE INDEX idx_orders_user_status ON orders(user_id, status);

-- Create index on created_at for sorting
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);


