package com.vv456.cart_service.client;

import com.vv456.cart_service.dto.InventoryCheckResponse;
import com.vv456.cart_service.dto.ReservationRequest;
import com.vv456.cart_service.dto.ReservationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "inventory-service", fallback = InventoryClientFallback.class)
public interface InventoryClient {

    @GetMapping("/api/products/{productId}")
    InventoryCheckResponse checkProductAvailability(@PathVariable("productId") String productId);

    @PostMapping("/api/inventory/reserve")
    ReservationResponse reserveItems(@RequestBody ReservationRequest request);

    @PostMapping("/api/inventory/reserve/{reservationId}/confirm")
    void confirmReservation(@PathVariable("reservationId") String reservationId);

    @DeleteMapping("/api/inventory/reserve/{reservationId}")
    void cancelReservation(@PathVariable("reservationId") String reservationId);
}
