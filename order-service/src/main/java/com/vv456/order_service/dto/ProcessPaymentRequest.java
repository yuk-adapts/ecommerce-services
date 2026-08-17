package com.vv456.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessPaymentRequest {

    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String paymentMethod;
    private String idempotencyKey;
}


