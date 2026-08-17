package com.vv456.cart_service.exception;

public class InsufficientStockException extends RuntimeException {
    private final Integer availableStock;

    public InsufficientStockException(String message, Integer availableStock) {
        super(message);
        this.availableStock = availableStock;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }
}

