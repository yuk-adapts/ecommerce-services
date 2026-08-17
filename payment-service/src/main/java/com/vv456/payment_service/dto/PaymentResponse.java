package com.vv456.payment_service.dto;

import com.vv456.payment_service.enums.PaymentMethod;
import com.vv456.payment_service.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;
    private Long orderId;
    private String idempotencyKey;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private String gatewayTransactionId;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    public static PaymentResponse success(Long id, Long orderId, BigDecimal amount, String gatewayTxnId) {
        return PaymentResponse.builder()
                .id(id)
                .orderId(orderId)
                .amount(amount)
                .status(PaymentStatus.SUCCESS)
                .gatewayTransactionId(gatewayTxnId)
                .build();
    }

    public static PaymentResponse failed(Long id, Long orderId, BigDecimal amount, String reason) {
        return PaymentResponse.builder()
                .id(id)
                .orderId(orderId)
                .amount(amount)
                .status(PaymentStatus.FAILED)
                .failureReason(reason)
                .build();
    }

    public boolean isSuccess() {
        return this.status == PaymentStatus.SUCCESS;
    }
}

