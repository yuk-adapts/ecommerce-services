package com.vv456.payment_service.entities;

import com.vv456.payment_service.enums.PaymentMethod;
import com.vv456.payment_service.enums.PaymentStatus;
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
@Table(name = "payments",
       uniqueConstraints = {
           // Critical: Prevents duplicate charges
           @UniqueConstraint(name = "uk_order_idempotency", 
                           columnNames = {"order_id", "idempotency_key"})
       },
       indexes = {
           @Index(name = "idx_order_id", columnList = "order_id"),
           @Index(name = "idx_gateway_transaction_id", columnList = "gateway_transaction_id"),
           @Index(name = "idx_status", columnList = "status"),
           @Index(name = "idx_created_at", columnList = "created_at")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Order ID cannot be null")
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    // Idempotency key - prevents duplicate charges on retries
    @NotNull(message = "Idempotency key cannot be null")
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 50)
    private PaymentMethod paymentMethod;

    @Column(name = "gateway_transaction_id", length = 255)
    private String gatewayTransactionId;

    @Column(name = "gateway_response", columnDefinition = "TEXT")
    private String gatewayResponse;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isSuccess() {
        return this.status == PaymentStatus.SUCCESS;
    }

    public boolean isFinalState() {
        return this.status == PaymentStatus.SUCCESS 
            || this.status == PaymentStatus.FAILED
            || this.status == PaymentStatus.CANCELLED
            || this.status == PaymentStatus.REFUNDED;
    }

    public boolean canBeRefunded() {
        return this.status == PaymentStatus.SUCCESS;
    }
}
