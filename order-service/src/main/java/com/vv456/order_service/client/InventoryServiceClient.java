package com.vv456.order_service.client;

import com.vv456.order_service.dto.BatchAvailabilityCheckRequest;
import com.vv456.order_service.dto.BatchAvailabilityCheckResponse;
import com.vv456.order_service.dto.BatchProductRequest;
import com.vv456.order_service.dto.ProductResponse;
import com.vv456.order_service.dto.ReserveInventoryRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "inventory-service")
public interface InventoryServiceClient {

    @GetMapping("/api/products/{productId}")
    ProductResponse getProduct(@PathVariable("productId") Long productId);

    @PostMapping("/api/products/batch")
    List<ProductResponse> getProductsByIds(@RequestBody BatchProductRequest request);

    @PostMapping("/api/inventory/check")
    Boolean checkAvailability(@RequestParam("productId") Long productId,
            @RequestParam("quantity") Integer quantity);

    @PostMapping("/api/inventory/check/batch")
    BatchAvailabilityCheckResponse checkBatchAvailability(@RequestBody BatchAvailabilityCheckRequest request);

    @PostMapping("/api/inventory/reserve")
    Boolean reserveInventory(@RequestBody ReserveInventoryRequest request);

    @PostMapping("/api/inventory/release/{orderId}")
    void releaseInventory(@PathVariable("orderId") Long orderId);
}
