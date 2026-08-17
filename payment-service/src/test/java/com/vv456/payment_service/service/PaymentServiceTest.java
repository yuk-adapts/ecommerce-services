package com.vv456.payment_service.service;

import com.vv456.payment_service.dto.PaymentRequest;
import com.vv456.payment_service.dto.PaymentResponse;
import com.vv456.payment_service.entities.Payment;
import com.vv456.payment_service.enums.PaymentMethod;
import com.vv456.payment_service.enums.PaymentStatus;
import com.vv456.payment_service.gateway.PaymentGateway;
import com.vv456.payment_service.gateway.PaymentGatewayResponse;
import com.vv456.payment_service.repository.PaymentRepository;
import com.vv456.payment_service.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Payment Service Unit Tests
 * Tests core payment processing logic including idempotency
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentRequest paymentRequest;
    private Payment payment;

    @BeforeEach
    void setUp() {
        paymentRequest = PaymentRequest.builder()
                .orderId(1L)
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        payment = Payment.builder()
                .id(1L)
                .orderId(1L)
                .idempotencyKey(paymentRequest.getIdempotencyKey())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.SUCCESS)
                .gatewayTransactionId("TXN-123")
                .build();
    }

    @Test
    void processPayment_NewPayment_Success() {
        // Arrange
        when(paymentRepository.findByOrderIdAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(payment);
        when(paymentGateway.processPayment(any()))
                .thenReturn(PaymentGatewayResponse.success("TXN-123"));

        // Act
        PaymentResponse response = paymentService.processPayment(paymentRequest);

        // Assert
        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals("TXN-123", response.getGatewayTransactionId());
        verify(paymentRepository, times(3)).save(any(Payment.class));
        verify(paymentGateway, times(1)).processPayment(any());
    }

    @Test
    void processPayment_DuplicateRequest_ReturnsExisting() {
        // Arrange
        when(paymentRepository.findByOrderIdAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.of(payment));

        // Act
        PaymentResponse response = paymentService.processPayment(paymentRequest);

        // Assert
        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals("TXN-123", response.getGatewayTransactionId());
        
        // Verify gateway was NOT called (idempotent behavior)
        verify(paymentGateway, never()).processPayment(any());
        
        // Verify payment was NOT saved again
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_GatewayFailure_ReturnsFailedStatus() {
        // Arrange
        when(paymentRepository.findByOrderIdAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGateway.processPayment(any()))
                .thenReturn(PaymentGatewayResponse.failure("CARD_DECLINED", "Card declined"));

        // Act
        PaymentResponse response = paymentService.processPayment(paymentRequest);

        // Assert
        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("Card declined", response.getFailureReason());
        verify(paymentGateway, times(1)).processPayment(any());
    }

    @Test
    void processPayment_GatewayException_HandlesGracefully() {
        // Arrange
        when(paymentRepository.findByOrderIdAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGateway.processPayment(any()))
                .thenThrow(new RuntimeException("Gateway timeout"));

        // Act
        PaymentResponse response = paymentService.processPayment(paymentRequest);

        // Assert
        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertTrue(response.getFailureReason().contains("Payment gateway error"));
    }
}

