package com.vv456.payment_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest {

    @NotNull(message = "Payment ID is required")
    private Long paymentId;

    // If null, full refund will be processed
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than 0")
    private BigDecimal amount;

    private String reason;
}

