package com.vv456.payment_service.service;

import com.vv456.payment_service.dto.*;
import com.vv456.payment_service.entities.Payment;
import com.vv456.payment_service.entities.Refund;
import com.vv456.payment_service.enums.PaymentStatus;
import com.vv456.payment_service.enums.RefundStatus;
import com.vv456.payment_service.exception.InvalidRefundException;
import com.vv456.payment_service.exception.PaymentException;
import com.vv456.payment_service.exception.PaymentNotFoundException;
import com.vv456.payment_service.gateway.PaymentGateway;
import com.vv456.payment_service.gateway.PaymentGatewayResponse;
import com.vv456.payment_service.repository.PaymentRepository;
import com.vv456.payment_service.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentGateway paymentGateway;

    // Critical: Idempotency prevents duplicate charges on retries
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment for order: {}, idempotency key: {}", 
                request.getOrderId(), request.getIdempotencyKey());

        // Check if this exact payment has been processed before
        Optional<Payment> existingPayment = paymentRepository.findByOrderIdAndIdempotencyKey(
                request.getOrderId(),
                request.getIdempotencyKey()
        );

        if (existingPayment.isPresent()) {
            log.info("Idempotent request detected for order: {}. Returning existing payment.", 
                    request.getOrderId());
            return mapToResponse(existingPayment.get());
        }

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .idempotencyKey(request.getIdempotencyKey())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .build();

        // Save immediately to reserve the idempotency key (prevents race conditions)
        payment = paymentRepository.save(payment);
        log.info("Payment record created with id: {} for order: {}", 
                payment.getId(), request.getOrderId());

        payment.setStatus(PaymentStatus.PROCESSING);
        payment = paymentRepository.save(payment);

        try {
            PaymentGatewayResponse gatewayResponse = paymentGateway.processPayment(request);

            if (gatewayResponse.isSuccess()) {
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setGatewayTransactionId(gatewayResponse.getTransactionId());
                payment.setGatewayResponse(gatewayResponse.getRawResponse());
                log.info("Payment successful for order: {}, transaction: {}", 
                        request.getOrderId(), gatewayResponse.getTransactionId());
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(gatewayResponse.getMessage());
                payment.setGatewayResponse(gatewayResponse.getRawResponse());
                log.warn("Payment failed for order: {}, reason: {}", 
                        request.getOrderId(), gatewayResponse.getMessage());
            }

        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment gateway error: " + e.getMessage());
            log.error("Payment gateway error for order: {}", request.getOrderId(), e);
        }

        payment = paymentRepository.save(payment);
        return mapToResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        log.info("Fetching payment with id: {}", id);
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return mapToResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByOrderId(Long orderId) {
        log.info("Fetching payments for order: {}", orderId);
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        return payments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // For timeout recovery
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentStatus(Long id) {
        log.info("Checking payment status for id: {}", id);
        return getPaymentById(id);
    }

    @Transactional
    public RefundResponse processRefund(RefundRequest request) {
        log.info("Processing refund for payment: {}", request.getPaymentId());

        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(request.getPaymentId()));

        if (!payment.canBeRefunded()) {
            throw new InvalidRefundException(
                    "Payment cannot be refunded. Current status: " + payment.getStatus());
        }

        // Full refund if amount not specified
        BigDecimal refundAmount = request.getAmount() != null 
                ? request.getAmount() 
                : payment.getAmount();

        BigDecimal totalRefunded = refundRepository.calculateTotalRefundedAmount(payment.getId());
        if (totalRefunded == null) {
            totalRefunded = BigDecimal.ZERO;
        }

        BigDecimal availableForRefund = payment.getAmount().subtract(totalRefunded);
        if (refundAmount.compareTo(availableForRefund) > 0) {
            throw new InvalidRefundException(
                    String.format("Refund amount %s exceeds available amount %s", 
                            refundAmount, availableForRefund));
        }

        Refund refund = Refund.builder()
                .paymentId(payment.getId())
                .amount(refundAmount)
                .reason(request.getReason())
                .status(RefundStatus.PENDING)
                .build();

        refund = refundRepository.save(refund);
        refund.setStatus(RefundStatus.PROCESSING);
        refund = refundRepository.save(refund);

        try {
            PaymentGatewayResponse gatewayResponse = paymentGateway.processRefund(
                    request, payment.getGatewayTransactionId()
            );

            if (gatewayResponse.isSuccess()) {
                refund.setStatus(RefundStatus.SUCCESS);
                refund.setGatewayRefundId(gatewayResponse.getTransactionId());
                log.info("Refund successful for payment: {}, refund id: {}", 
                        payment.getId(), refund.getId());

                // Mark payment as REFUNDED if fully refunded
                BigDecimal newTotalRefunded = totalRefunded.add(refundAmount);
                if (newTotalRefunded.compareTo(payment.getAmount()) == 0) {
                    payment.setStatus(PaymentStatus.REFUNDED);
                    paymentRepository.save(payment);
                    log.info("Payment {} fully refunded", payment.getId());
                }

            } else {
                refund.setStatus(RefundStatus.FAILED);
                log.warn("Refund failed for payment: {}, reason: {}", 
                        payment.getId(), gatewayResponse.getMessage());
            }

        } catch (Exception e) {
            refund.setStatus(RefundStatus.FAILED);
            log.error("Refund gateway error for payment: {}", payment.getId(), e);
        }

        refund = refundRepository.save(refund);
        return mapToRefundResponse(refund);
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByPaymentId(Long paymentId) {
        log.info("Fetching refunds for payment: {}", paymentId);
        List<Refund> refunds = refundRepository.findByPaymentId(paymentId);
        return refunds.stream()
                .map(this::mapToRefundResponse)
                .collect(Collectors.toList());
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .idempotencyKey(payment.getIdempotencyKey())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .gatewayTransactionId(payment.getGatewayTransactionId())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private RefundResponse mapToRefundResponse(Refund refund) {
        return RefundResponse.builder()
                .id(refund.getId())
                .paymentId(refund.getPaymentId())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .status(refund.getStatus())
                .gatewayRefundId(refund.getGatewayRefundId())
                .createdAt(refund.getCreatedAt())
                .updatedAt(refund.getUpdatedAt())
                .build();
    }
}

