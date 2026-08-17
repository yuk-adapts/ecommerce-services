package com.vv456.cart_service.client;

import com.vv456.cart_service.dto.InventoryCheckResponse;
import com.vv456.cart_service.dto.ReservationRequest;
import com.vv456.cart_service.dto.ReservationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class InventoryClientFallback implements InventoryClient {

    @Override
    public InventoryCheckResponse checkProductAvailability(String productId) {
        log.warn("Inventory service is unavailable. Using fallback for product: {}", productId);

        return InventoryCheckResponse.builder()
                .id(Long.parseLong(productId))
                .name("Product information unavailable")
                .price(BigDecimal.ZERO)
                .availableQuantity(0)
                .reservedQuantity(0)
                .build();
    }

    @Override
    public ReservationResponse reserveItems(ReservationRequest request) {
        log.warn("Inventory service is unavailable. Cannot reserve items for user: {}", request.getUserId());

        return ReservationResponse.builder()
                .userId(request.getUserId())
                .success(false)
                .message("Inventory service is currently unavailable. Please try again later.")
                .build();
    }

    @Override
    public void confirmReservation(String reservationId) {
        log.warn("Inventory service is unavailable. Cannot confirm reservation: {}", reservationId);
    }

    @Override
    public void cancelReservation(String reservationId) {
        log.warn("Inventory service is unavailable. Cannot cancel reservation: {}", reservationId);
    }
}
