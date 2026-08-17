package com.vv456.order_service.repository;

import com.vv456.order_service.entities.Order;
import com.vv456.order_service.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.userId = :userId")
    List<Order> findByUserIdWithItems(@Param("userId") Long userId);

    // Find expired pending orders for auto-cancellation
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, Instant createdBefore);
}
