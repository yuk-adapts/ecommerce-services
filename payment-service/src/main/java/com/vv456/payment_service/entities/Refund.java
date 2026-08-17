package com.vv456.payment_service.entities;

import com.vv456.payment_service.enums.RefundStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refunds",
       indexes = {
           @Index(name = "idx_payment_id", columnList = "payment_id"),
           @Index(name = "idx_status", columnList = "status")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Payment ID cannot be null")
    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than 0")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String reason;

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RefundStatus status;

    @Column(name = "gateway_refund_id", length = 255)
    private String gatewayRefundId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

