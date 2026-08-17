package com.vv456.payment_service.exception;

public class InvalidRefundException extends PaymentException {

    public InvalidRefundException(String message) {
        super(message);
    }
}

