package com.vv456.payment_service.gateway;

import com.vv456.payment_service.dto.PaymentRequest;
import com.vv456.payment_service.dto.RefundRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Mock Payment Gateway - Simulates real gateway behavior for testing
 * 
 * Test Patterns (deterministic testing):
 * - Amount ending in .99 → Always succeeds
 * - Amount ending in .00 → Always fails (insufficient funds)
 * - Amount ending in .01 → Card declined
 * - Other amounts → 80% success, 20% random failure
 * - Random delays: 100ms - 2000ms to simulate network
 */
@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final Random random = new Random();
    private final Map<String, PaymentGatewayResponse> transactions = new ConcurrentHashMap<>();

    @Override
    public PaymentGatewayResponse processPayment(PaymentRequest request) {
        log.info("Processing payment for order: {}, amount: {}", 
                request.getOrderId(), request.getAmount());

        simulateDelay();

        BigDecimal amount = request.getAmount();
        PaymentGatewayResponse response;

        if (isTestSuccessAmount(amount)) {
            response = processSuccessfulPayment(request);
        } else if (isTestFailureAmount(amount)) {
            response = processFailedPayment(request, "INSUFFICIENT_FUNDS", 
                    "Insufficient funds in customer account");
        } else if (isTestDeclinedAmount(amount)) {
            response = processFailedPayment(request, "CARD_DECLINED", 
                    "Card declined by issuing bank");
        } else {
            if (random.nextInt(100) < 80) {
                response = processSuccessfulPayment(request);
            } else {
                response = processRandomFailure(request);
            }
        }

        if (response.getTransactionId() != null) {
            transactions.put(response.getTransactionId(), response);
        }

        log.info("Payment processing result for order {}: {}", 
                request.getOrderId(), response.isSuccess() ? "SUCCESS" : "FAILED");

        return response;
    }

    @Override
    public PaymentGatewayResponse processRefund(RefundRequest request, String originalTransactionId) {
        log.info("Processing refund for payment transaction: {}, amount: {}", 
                originalTransactionId, request.getAmount());

        simulateDelay();

        if (!transactions.containsKey(originalTransactionId)) {
            return PaymentGatewayResponse.failure("TRANSACTION_NOT_FOUND", 
                    "Original transaction not found");
        }

        // Refunds have 95% success rate
        if (random.nextInt(100) < 95) {
            String refundTxnId = "REFUND-" + UUID.randomUUID().toString();
            log.info("Refund successful: {}", refundTxnId);
            return PaymentGatewayResponse.success(refundTxnId);
        } else {
            return PaymentGatewayResponse.failure("REFUND_FAILED", 
                    "Refund processing failed");
        }
    }

    @Override
    public PaymentGatewayResponse checkPaymentStatus(String transactionId) {
        log.info("Checking payment status for transaction: {}", transactionId);
        
        PaymentGatewayResponse storedResponse = transactions.get(transactionId);
        if (storedResponse != null) {
            return storedResponse;
        }

        return PaymentGatewayResponse.failure("TRANSACTION_NOT_FOUND", 
                "Transaction not found in gateway");
    }

    private PaymentGatewayResponse processSuccessfulPayment(PaymentRequest request) {
        String transactionId = "TXN-" + UUID.randomUUID().toString();
        return PaymentGatewayResponse.builder()
                .success(true)
                .transactionId(transactionId)
                .message("Payment processed successfully")
                .rawResponse(buildRawResponse(request, transactionId, "SUCCESS"))
                .build();
    }

    private PaymentGatewayResponse processFailedPayment(PaymentRequest request, 
                                                         String errorCode, 
                                                         String message) {
        return PaymentGatewayResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .rawResponse(buildRawResponse(request, null, errorCode))
                .build();
    }

    private PaymentGatewayResponse processRandomFailure(PaymentRequest request) {
        String[] errors = {
                "INSUFFICIENT_FUNDS:Insufficient funds in customer account",
                "CARD_DECLINED:Card declined by issuing bank",
                "CARD_EXPIRED:Card has expired",
                "INVALID_CVV:Invalid CVV code",
                "FRAUD_SUSPECTED:Transaction flagged for potential fraud",
                "GATEWAY_ERROR:Gateway processing error"
        };

        String[] errorParts = errors[random.nextInt(errors.length)].split(":");
        return processFailedPayment(request, errorParts[0], errorParts[1]);
    }

    private void simulateDelay() {
        try {
            int delayMs = 100 + random.nextInt(1900);
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Payment processing delay interrupted", e);
        }
    }

    // Amount ending in .99 always succeeds
    private boolean isTestSuccessAmount(BigDecimal amount) {
        return amount.toString().endsWith(".99");
    }

    // Amount ending in .00 always fails
    private boolean isTestFailureAmount(BigDecimal amount) {
        return amount.toString().endsWith(".00");
    }

    // Amount ending in .01 always declined
    private boolean isTestDeclinedAmount(BigDecimal amount) {
        return amount.toString().endsWith(".01");
    }

    private String buildRawResponse(PaymentRequest request, String transactionId, String status) {
        return String.format(
                "{\"gateway\":\"MockGateway\",\"orderId\":\"%s\",\"amount\":\"%s\",\"transactionId\":\"%s\",\"status\":\"%s\",\"timestamp\":\"%d\"}",
                request.getOrderId(),
                request.getAmount(),
                transactionId != null ? transactionId : "N/A",
                status,
                System.currentTimeMillis()
        );
    }
}

