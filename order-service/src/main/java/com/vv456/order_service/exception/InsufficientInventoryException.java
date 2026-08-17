package com.vv456.order_service.exception;

public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(String message) {
        super(message);
    }

    public InsufficientInventoryException(Long productId, Integer requested) {
        super(String.format("Insufficient inventory for product ID %d. Requested: %d", productId, requested));
    }
}


