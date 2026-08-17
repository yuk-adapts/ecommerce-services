package com.vv456.payment_service.dto;

import com.vv456.payment_service.enums.RefundStatus;
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
public class RefundResponse {

    private Long id;
    private Long paymentId;
    private BigDecimal amount;
    private String reason;
    private RefundStatus status;
    private String gatewayRefundId;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isSuccess() {
        return this.status == RefundStatus.SUCCESS;
    }
}

