package com.vv456.cart_service.scheduler;

import com.vv456.cart_service.model.Cart;
import com.vv456.cart_service.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CartCleanupScheduler {

    private final CartRepository cartRepository;

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredCarts() {
        log.info("Starting cleanup of expired carts...");
        
        try {
            List<Cart> expiredCarts = cartRepository.findByExpiresAtBefore(LocalDateTime.now());
            
            if (!expiredCarts.isEmpty()) {
                cartRepository.deleteAll(expiredCarts);
                log.info("Cleaned up {} expired carts", expiredCarts.size());
            } else {
                log.info("No expired carts found");
            }
        } catch (Exception e) {
            log.error("Error cleaning up expired carts", e);
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void logCartStatistics() {
        try {
            long totalCarts = cartRepository.count();
            log.info("Total active carts: {}", totalCarts);
        } catch (Exception e) {
            log.error("Error logging cart statistics", e);
        }
    }
}

