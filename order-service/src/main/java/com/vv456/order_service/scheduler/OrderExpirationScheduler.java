package com.vv456.order_service.scheduler;

import com.vv456.order_service.entities.Order;
import com.vv456.order_service.enums.OrderStatus;
import com.vv456.order_service.repository.OrderRepository;
import com.vv456.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled task to automatically cancel expired pending orders
 * This prevents inventory from being locked indefinitely
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${order.pending.expiration.minutes:15}")
    private int expirationMinutes;

    /**
     * Check for expired pending orders every minute
     */
    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void cancelExpiredOrders() {
        log.debug("Checking for expired pending orders");

        Instant expirationTime = Instant.now().minus(expirationMinutes, ChronoUnit.MINUTES);

        List<Order> expiredOrders = orderRepository
                .findByStatusAndCreatedAtBefore(OrderStatus.PENDING, expirationTime);

        if (expiredOrders.isEmpty()) {
            log.debug("No expired orders found");
            return;
        }

        log.info("Found {} expired orders to cancel", expiredOrders.size());

        for (Order order : expiredOrders) {
            try {
                log.info("Auto-cancelling expired order: {} created at {}", 
                        order.getId(), order.getCreatedAt());
                
                orderService.cancelOrder(order.getId(), order.getUserId());
                
                log.info("Successfully cancelled expired order: {}", order.getId());
            } catch (Exception e) {
                log.error("Failed to cancel expired order: {}", order.getId(), e);
            }
        }
    }
}

