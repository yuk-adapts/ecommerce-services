-- Create payments table with idempotency support
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status ENUM('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED', 'TIMEOUT') NOT NULL,
    payment_method ENUM('CREDIT_CARD', 'DEBIT_CARD', 'UPI', 'WALLET', 'NET_BANKING', 'CASH_ON_DELIVERY'),
    gateway_transaction_id VARCHAR(255),
    gateway_response TEXT,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Idempotency constraint: Same order cannot be charged twice with same key
    UNIQUE KEY uk_order_idempotency (order_id, idempotency_key),
    
    -- Index for quick lookup by order
    INDEX idx_order_id (order_id),
    
    -- Index for quick lookup by gateway transaction
    INDEX idx_gateway_transaction_id (gateway_transaction_id),
    
    -- Index for status queries
    INDEX idx_status (status),
    
    -- Index for created_at for time-based queries
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

