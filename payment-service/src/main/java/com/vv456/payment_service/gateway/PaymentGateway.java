package com.vv456.payment_service.gateway;

import com.vv456.payment_service.dto.PaymentRequest;
import com.vv456.payment_service.dto.RefundRequest;

// Abstraction for payment gateway operations (can be implemented by Stripe, Razorpay, etc.)
public interface PaymentGateway {

    PaymentGatewayResponse processPayment(PaymentRequest request);

    PaymentGatewayResponse processRefund(RefundRequest request, String originalTransactionId);

    PaymentGatewayResponse checkPaymentStatus(String transactionId);
}

