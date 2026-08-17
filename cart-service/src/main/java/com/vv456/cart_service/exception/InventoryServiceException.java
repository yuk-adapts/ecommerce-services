package com.vv456.cart_service.exception;

public class InventoryServiceException extends RuntimeException {
    public InventoryServiceException(String message) {
        super(message);
    }
    
    public InventoryServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

