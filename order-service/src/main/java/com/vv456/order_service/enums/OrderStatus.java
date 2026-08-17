package com.vv456.order_service.enums;

public enum OrderStatus {
    PENDING,        // Order created, awaiting payment
    CONFIRMED,      // Payment successful, order confirmed
    PROCESSING,     // Order being processed
    SHIPPED,        // Order shipped
    DELIVERED,      // Order delivered
    CANCELLED,      // Order cancelled
    REFUNDED        // Order refunded
}


