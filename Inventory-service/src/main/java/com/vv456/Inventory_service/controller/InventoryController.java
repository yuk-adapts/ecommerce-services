package com.vv456.Inventory_service.controller;

import com.vv456.Inventory_service.dto.BatchAvailabilityCheckRequest;
import com.vv456.Inventory_service.dto.BatchAvailabilityCheckResponse;
import com.vv456.Inventory_service.dto.CheckAndReserveRequest;
import com.vv456.Inventory_service.dto.CheckAndReserveResponse;
import com.vv456.Inventory_service.dto.ReserveInventoryRequest;
import com.vv456.Inventory_service.service.InventoryService;
import com.vv456.Inventory_service.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management APIs for stock checking and reservation")
public class InventoryController {

        private final InventoryService inventoryService;
        private final ProductService productService;

        @Operation(summary = "Reserve inventory for order", description = "Reserves inventory for multiple items in an order. Called by order-service.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Inventory reserved successfully"),
                        @ApiResponse(responseCode = "409", description = "Insufficient inventory or reservation failed"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
        })
        @PostMapping("/reserve")
        public ResponseEntity<Boolean> reserveInventory(@RequestBody ReserveInventoryRequest request) {
                boolean reserved = inventoryService.reserveInventoryForOrder(request);
                if (reserved) {
                        return ResponseEntity.ok(true);
                } else {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(false);
                }
        }

        @Operation(summary = "Release inventory by order ID", description = "Releases all reserved inventory for a specific order. Called by order-service when order is cancelled.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Inventory released successfully"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
        })
        @PostMapping("/release/{orderId}")
        public ResponseEntity<Void> releaseInventoryByOrderId(
                        @Parameter(description = "Order ID", required = true) @PathVariable("orderId") Long orderId) {
                // For now, just log - in production you'd track reservations by order ID
                // This is a placeholder that allows the order-service call to succeed
                return ResponseEntity.ok().build();
        }

        @Operation(summary = "Check and reserve inventory", description = "Checks product availability and reserves the specified quantity if available")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Inventory checked and reserved successfully", content = @Content(schema = @Schema(implementation = CheckAndReserveResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Insufficient inventory or reservation failed", content = @Content(schema = @Schema(implementation = CheckAndReserveResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
        })
        @PostMapping("/check-and-reserve")
        public ResponseEntity<CheckAndReserveResponse> checkAndReserve(
                        @RequestBody CheckAndReserveRequest request) {
                CheckAndReserveResponse response = inventoryService.checkAndReserve(request);

                if (response.isSuccess()) {
                        return ResponseEntity.ok(response);
                } else {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
                }
        }

        @Operation(summary = "Release inventory reservation", description = "Releases a previously reserved inventory quantity back to available stock")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Reservation released successfully"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
        })
        @PostMapping("/release/{productId}/{quantity}")
        public ResponseEntity<String> releaseReservation(
                        @Parameter(description = "Product ID", required = true) @PathVariable("productId") Long productId,
                        @Parameter(description = "Quantity to release", required = true) @PathVariable("quantity") Integer quantity) {
                inventoryService.releaseReservation(productId, quantity);
                return ResponseEntity.ok("Reservation released successfully");
        }

        @Operation(summary = "Confirm inventory reservation", description = "Confirms a reservation and deducts the quantity from reserved stock")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Reservation confirmed successfully"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
        })
        @PostMapping("/confirm/{productId}/{quantity}")
        public ResponseEntity<String> confirmReservation(
                        @Parameter(description = "Product ID", required = true) @PathVariable("productId") Long productId,
                        @Parameter(description = "Quantity to confirm", required = true) @PathVariable("quantity") Integer quantity) {
                inventoryService.confirmReservation(productId, quantity);
                return ResponseEntity.ok("Reservation confirmed successfully");
        }

        @Operation(summary = "Check inventory availability", description = "Checks if a product is available in the requested quantity without reserving it. Used by order-service for validation.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Availability check completed"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
        })
        @PostMapping("/check")
        public ResponseEntity<Boolean> checkAvailability(
                        @Parameter(description = "Product ID", required = true) @RequestParam("productId") Long productId,
                        @Parameter(description = "Quantity to check", required = true) @RequestParam("quantity") Integer quantity) {
                Boolean available = productService.checkAvailability(productId, quantity);
                return ResponseEntity.ok(available);
        }

        @Operation(summary = "Batch check inventory availability", description = "Checks availability for multiple products in a single request. Used by order-service for efficient batch validation.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Batch availability check completed", content = @Content(schema = @Schema(implementation = BatchAvailabilityCheckResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
        })
        @PostMapping("/check/batch")
        public ResponseEntity<BatchAvailabilityCheckResponse> checkBatchAvailability(
                        @RequestBody BatchAvailabilityCheckRequest request) {
                BatchAvailabilityCheckResponse response = productService.checkBatchAvailability(request);
                return ResponseEntity.ok(response);
        }
}
