package com.vv456.order_service.controller;

import com.vv456.order_service.dto.CreateOrderRequest;
import com.vv456.order_service.dto.OrderResponse;
import com.vv456.order_service.enums.OrderStatus;
import com.vv456.order_service.security.UserDetailsHelper;
import com.vv456.order_service.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "APIs for managing orders with SAGA orchestration pattern")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

        private final OrderService orderService;

        @Operation(summary = "Validate order items", description = "Validates order items without creating the order. Checks inventory availability and calculates total amount. Use this before checkout.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Validation successful", content = @Content(mediaType = "application/json")),
                        @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content),
                        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
        })
        @PostMapping("/validate")
        public ResponseEntity<Map<String, Object>> validateOrder(
                        @Parameter(description = "Order details to validate", required = true) @Valid @RequestBody CreateOrderRequest request,
                        Authentication authentication) {

                Long userId = UserDetailsHelper.getUserIdFromAuthentication(authentication);
                log.info("Validate order request from user: {}", userId);

                Map<String, Object> validation = orderService.validateOrderItems(request);
                return ResponseEntity.ok(validation);
        }

        @Operation(summary = "Create a new order", description = "Creates a new order with SAGA orchestration. Reserves inventory from inventory service. Status will be PENDING until payment is processed. This should only be called at checkout.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Order created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid request or insufficient inventory", content = @Content),
                        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                        @ApiResponse(responseCode = "503", description = "Service unavailable (circuit breaker open)", content = @Content)
        })
        @PostMapping
        public ResponseEntity<OrderResponse> createOrder(
                        @Parameter(description = "Order creation request with items", required = true) @Valid @RequestBody CreateOrderRequest request,
                        Authentication authentication) {

                Long userId = UserDetailsHelper.getUserIdFromAuthentication(authentication);
                log.info("Create order request from user: {}", userId);

                OrderResponse response = orderService.createOrder(request, userId);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @Operation(summary = "Process payment for an order", description = "Processes payment for a pending order. Communicates with payment service. On success, order status changes to PAID. On failure, inventory is released and order is cancelled.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Payment processed successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid order status or payment details", content = @Content),
                        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                        @ApiResponse(responseCode = "404", description = "Order not found", content = @Content),
                        @ApiResponse(responseCode = "503", description = "Payment service unavailable", content = @Content)
        })
        @PostMapping("/{orderId}/payment")
        public ResponseEntity<OrderResponse> processPayment(
                        @Parameter(description = "Order ID", required = true, example = "1") @PathVariable("orderId") Long orderId,
                        @Parameter(description = "Payment details (paymentMethod: CREDIT_CARD, DEBIT_CARD, etc.)") @RequestBody Map<String, String> paymentRequest,
                        Authentication authentication) {

                Long userId = UserDetailsHelper.getUserIdFromAuthentication(authentication);
                String paymentMethod = paymentRequest.getOrDefault("paymentMethod", "CREDIT_CARD");

                log.info("Process payment request for order: {} by user: {}", orderId, userId);

                OrderResponse response = orderService.processPayment(orderId, paymentMethod, userId);
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Cancel an order", description = "Cancels an order and releases reserved inventory. Only PENDING orders can be cancelled.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Order cancelled successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Order cannot be cancelled (already paid or cancelled)", content = @Content),
                        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                        @ApiResponse(responseCode = "404", description = "Order not found", content = @Content)
        })
        @PostMapping("/{orderId}/cancel")
        public ResponseEntity<OrderResponse> cancelOrder(
                        @Parameter(description = "Order ID to cancel", required = true, example = "1") @PathVariable("orderId") Long orderId,
                        Authentication authentication) {

                Long userId = UserDetailsHelper.getUserIdFromAuthentication(authentication);
                log.info("Cancel order request for order: {} by user: {}", orderId, userId);

                OrderResponse response = orderService.cancelOrder(orderId, userId);
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Get order by ID", description = "Retrieves a specific order by its ID. Only the order owner can access it.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Order retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                        @ApiResponse(responseCode = "403", description = "Forbidden - Not the order owner", content = @Content),
                        @ApiResponse(responseCode = "404", description = "Order not found", content = @Content)
        })
        @GetMapping("/{orderId}")
        public ResponseEntity<OrderResponse> getOrderById(
                        @Parameter(description = "Order ID", required = true, example = "1") @PathVariable("orderId") Long orderId,
                        Authentication authentication) {

                Long userId = UserDetailsHelper.getUserIdFromAuthentication(authentication);
                log.debug("Get order request for order: {} by user: {}", orderId, userId);

                OrderResponse response = orderService.getOrderById(orderId, userId);
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Get all orders for authenticated user", description = "Retrieves all orders for the currently authenticated user")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Orders retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
        })
        @GetMapping
        public ResponseEntity<List<OrderResponse>> getUserOrders(Authentication authentication) {
                Long userId = UserDetailsHelper.getUserIdFromAuthentication(authentication);
                log.debug("Get all orders request for user: {}", userId);

                List<OrderResponse> orders = orderService.getUserOrders(userId);
                return ResponseEntity.ok(orders);
        }

        @Operation(summary = "Get orders by status", description = "Retrieves all orders for the authenticated user filtered by status (PENDING, PAID, CANCELLED)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Orders retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid status value", content = @Content),
                        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
        })
        @GetMapping("/status/{status}")
        public ResponseEntity<List<OrderResponse>> getUserOrdersByStatus(
                        @Parameter(description = "Order status (PENDING, PAID, CANCELLED)", required = true, example = "PENDING") @PathVariable("status") String status,
                        Authentication authentication) {

                Long userId = UserDetailsHelper.getUserIdFromAuthentication(authentication);
                log.debug("Get orders with status {} for user: {}", status, userId);

                OrderStatus orderStatus;
                try {
                        orderStatus = OrderStatus.valueOf(status.toUpperCase());
                } catch (IllegalArgumentException e) {
                        log.warn("Invalid order status: {}", status);
                        return ResponseEntity.badRequest().build();
                }

                List<OrderResponse> orders = orderService.getUserOrdersByStatus(userId, orderStatus);
                return ResponseEntity.ok(orders);
        }

        @Operation(summary = "Health check", description = "Simple health check endpoint to verify the service is running")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Service is healthy", content = @Content(mediaType = "application/json"))
        })
        @GetMapping("/health")
        public ResponseEntity<Map<String, String>> healthCheck() {
                Map<String, String> health = new HashMap<>();
                health.put("status", "UP");
                health.put("service", "order-service");
                return ResponseEntity.ok(health);
        }
}
