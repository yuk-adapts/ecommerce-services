package com.vv456.payment_service.repository;

import com.vv456.payment_service.entities.Refund;
import com.vv456.payment_service.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByPaymentId(Long paymentId);

    List<Refund> findByStatus(RefundStatus status);

    @Query("SELECT SUM(r.amount) FROM Refund r WHERE r.paymentId = :paymentId AND r.status = 'SUCCESS'")
    BigDecimal calculateTotalRefundedAmount(@Param("paymentId") Long paymentId);

    boolean existsByPaymentIdAndStatus(Long paymentId, RefundStatus status);
}

