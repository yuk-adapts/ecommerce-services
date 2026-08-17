package com.vv456.payment_service.exception;

public class PaymentNotFoundException extends PaymentException {

    public PaymentNotFoundException(Long id) {
        super("Payment not found with id: " + id);
    }

    public PaymentNotFoundException(String message) {
        super(message);
    }
}

