package com.vv456.order_service.exception;

public class UnauthorizedOrderAccessException extends RuntimeException {
    public UnauthorizedOrderAccessException(String message) {
        super(message);
    }

    public UnauthorizedOrderAccessException(Long orderId, Long userId) {
        super(String.format("User %d is not authorized to access order %d", userId, orderId));
    }
}


