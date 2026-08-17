-- Create refunds table
CREATE TABLE refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    reason VARCHAR(500),
    status ENUM('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED') NOT NULL,
    gateway_refund_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Foreign key to payments
    FOREIGN KEY (payment_id) REFERENCES payments(id),
    
    -- Index for quick lookup by payment
    INDEX idx_payment_id (payment_id),
    
    -- Index for status queries
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

