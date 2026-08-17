package com.vv456.payment_service.repository;

import com.vv456.payment_service.entities.Payment;
import com.vv456.payment_service.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Critical for idempotency checks
    Optional<Payment> findByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);

    List<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);

    List<Payment> findByStatus(PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findPaymentsBetweenDates(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    // Find payments stuck in PROCESSING state (for timeout recovery)
    @Query("SELECT p FROM Payment p WHERE p.status = 'PROCESSING' AND p.createdAt < :timeout")
    List<Payment> findStalledPayments(@Param("timeout") Instant timeout);

    boolean existsByOrderId(Long orderId);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.orderId = :orderId AND p.status = 'SUCCESS'")
    long countSuccessfulPaymentsByOrderId(@Param("orderId") Long orderId);
}

