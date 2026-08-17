package com.vv456.order_service.service;

import com.vv456.order_service.client.InventoryServiceClient;
import com.vv456.order_service.client.PaymentServiceClient;
import com.vv456.order_service.dto.*;
import com.vv456.order_service.entities.Order;
import com.vv456.order_service.entities.OrderItem;
import com.vv456.order_service.enums.OrderStatus;
import com.vv456.order_service.exception.*;
import com.vv456.order_service.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryServiceClient inventoryServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    /**
     * Validate order items without creating order or reserving inventory
     * Used for checkout page validation
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validateOrderItems(CreateOrderRequest request) {
        log.info("Validating order items");

        List<Map<String, Object>> validations = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        boolean allAvailable = true;

        try {
            // Batch fetch products
            List<Long> productIds = request.getItems().stream()
                    .map(OrderItemRequest::getProductId)
                    .collect(Collectors.toList());

            BatchProductRequest batchProductRequest = BatchProductRequest.builder()
                    .productIds(productIds)
                    .build();

            List<ProductResponse> products = inventoryServiceClient.getProductsByIds(batchProductRequest);
            Map<Long, ProductResponse> productMap = products.stream()
                    .collect(Collectors.toMap(ProductResponse::getId, p -> p));

            // Batch check availability
            List<BatchAvailabilityCheckRequest.AvailabilityItem> availabilityItems = request.getItems().stream()
                    .map(item -> BatchAvailabilityCheckRequest.AvailabilityItem.builder()
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .build())
                    .collect(Collectors.toList());

            BatchAvailabilityCheckRequest batchAvailabilityRequest = BatchAvailabilityCheckRequest.builder()
                    .items(availabilityItems)
                    .build();

            BatchAvailabilityCheckResponse availabilityResponse = inventoryServiceClient
                    .checkBatchAvailability(batchAvailabilityRequest);
            Map<Long, Boolean> availabilityMap = availabilityResponse.getAvailability();

            // Process results
            for (OrderItemRequest itemRequest : request.getItems()) {
                ProductResponse product = productMap.get(itemRequest.getProductId());
                Boolean available = availabilityMap.get(itemRequest.getProductId());

                Map<String, Object> itemValidation = new HashMap<>();
                itemValidation.put("productId", itemRequest.getProductId());

                if (product != null) {
                    itemValidation.put("productName", product.getName());
                    itemValidation.put("price", product.getPrice());
                } else {
                    allAvailable = false;
                    itemValidation.put("available", false);
                    itemValidation.put("message", "Product not found");
                    validations.add(itemValidation);
                    continue;
                }

                itemValidation.put("requestedQuantity", itemRequest.getQuantity());
                itemValidation.put("available", available != null ? available : false);

                if (Boolean.TRUE.equals(available)) {
                    BigDecimal subtotal = product.getPrice()
                            .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
                    itemValidation.put("subtotal", subtotal);
                    totalAmount = totalAmount.add(subtotal);
                } else {
                    allAvailable = false;
                    itemValidation.put("message", "Insufficient inventory");
                }

                validations.add(itemValidation);
            }

        } catch (Exception e) {
            log.error("Failed to validate order items: {}", e.getMessage(), e);
            allAvailable = false;

            // Create error validations for all items
            for (OrderItemRequest itemRequest : request.getItems()) {
                Map<String, Object> itemValidation = new HashMap<>();
                itemValidation.put("productId", itemRequest.getProductId());
                itemValidation.put("available", false);
                itemValidation.put("message", "Product validation failed");
                validations.add(itemValidation);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("valid", allAvailable);
        result.put("items", validations);
        result.put("totalAmount", totalAmount);
        result.put("timestamp", Instant.now());

        return result;
    }

    /**
     * Create a new order (without payment processing)
     * This follows the Saga pattern:
     * 1. Validate inventory availability
     * 2. Reserve inventory
     * 3. Create order in PENDING status
     * 4. If any step fails, compensate previous steps
     */
    @Transactional
    @CircuitBreaker(name = "orderService", fallbackMethod = "createOrderFallback")
    public OrderResponse createOrder(CreateOrderRequest request, Long userId) {
        log.info("Creating order for user: {}", userId);

        // Step 1: Validate all items and get product details (batch operation)
        List<Long> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .collect(Collectors.toList());

        List<ProductResponse> products;
        try {
            BatchProductRequest batchProductRequest = BatchProductRequest.builder()
                    .productIds(productIds)
                    .build();
            products = inventoryServiceClient.getProductsByIds(batchProductRequest);
        } catch (Exception e) {
            log.error("Failed to fetch products: {}", e.getMessage());
            throw new OrderException("Failed to fetch products: " + e.getMessage(), e);
        }

        // Create product map for quick lookup
        Map<Long, ProductResponse> productMap = products.stream()
                .collect(Collectors.toMap(ProductResponse::getId, p -> p));

        // Verify all products were found
        for (OrderItemRequest itemRequest : request.getItems()) {
            if (!productMap.containsKey(itemRequest.getProductId())) {
                throw new OrderException("Product not found: " + itemRequest.getProductId());
            }
        }

        // Step 1b: Batch check inventory availability
        List<BatchAvailabilityCheckRequest.AvailabilityItem> availabilityItems = request.getItems().stream()
                .map(item -> BatchAvailabilityCheckRequest.AvailabilityItem.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        BatchAvailabilityCheckRequest batchAvailabilityRequest = BatchAvailabilityCheckRequest.builder()
                .items(availabilityItems)
                .build();

        BatchAvailabilityCheckResponse availabilityResponse;
        try {
            availabilityResponse = inventoryServiceClient.checkBatchAvailability(batchAvailabilityRequest);
        } catch (Exception e) {
            log.error("Failed to check availability: {}", e.getMessage());
            throw new OrderException("Failed to check availability: " + e.getMessage(), e);
        }

        Map<Long, Boolean> availabilityMap = availabilityResponse.getAvailability();

        // Validate all items are available
        for (OrderItemRequest itemRequest : request.getItems()) {
            Boolean available = availabilityMap.get(itemRequest.getProductId());
            if (available == null || !available) {
                throw new InsufficientInventoryException(itemRequest.getProductId(), itemRequest.getQuantity());
            }
        }

        // Step 2: Calculate total amount
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest itemRequest : request.getItems()) {
            ProductResponse product = productMap.get(itemRequest.getProductId());

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(itemRequest.getQuantity())
                    .price(product.getPrice())
                    .subtotal(subtotal)
                    .build();

            orderItems.add(orderItem);
        }

        // Step 3: Create order entity
        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .items(orderItems)
                .build();

        // Set bidirectional relationship
        orderItems.forEach(item -> item.setOrder(order));

        // Step 4: Save order
        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully with ID: {}", savedOrder.getId());

        // Step 5: Reserve inventory
        try {
            ReserveInventoryRequest reserveRequest = buildReserveInventoryRequest(savedOrder);
            Boolean reserved = inventoryServiceClient.reserveInventory(reserveRequest);

            if (!reserved) {
                // Rollback: Delete the order
                orderRepository.delete(savedOrder);
                throw new OrderException("Failed to reserve inventory for order");
            }
        } catch (Exception e) {
            log.error("Failed to reserve inventory for order {}: {}", savedOrder.getId(), e.getMessage());
            // Rollback: Delete the order
            orderRepository.delete(savedOrder);
            throw new OrderException("Failed to reserve inventory: " + e.getMessage(), e);
        }

        return mapToOrderResponse(savedOrder);
    }

    /**
     * Process payment for an order
     * This completes the order saga:
     * 1. Process payment
     * 2. Update order status to CONFIRMED
     * 3. If payment fails, release inventory
     */
    @Transactional
    @CircuitBreaker(name = "orderService", fallbackMethod = "processPaymentFallback")
    public OrderResponse processPayment(Long orderId, String paymentMethod, Long userId) {
        log.info("Processing payment for order: {} by user: {}", orderId, userId);

        // Fetch order with items
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Verify ownership
        if (!order.getUserId().equals(userId)) {
            throw new UnauthorizedOrderAccessException(orderId, userId);
        }

        // Check if order is in valid state for payment
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderException("Order is not in PENDING status. Current status: " + order.getStatus());
        }

        // Create payment request with idempotency key
        String idempotencyKey = UUID.randomUUID().toString();
        ProcessPaymentRequest paymentRequest = ProcessPaymentRequest.builder()
                .orderId(order.getId())
                .userId(userId)
                .amount(order.getTotalAmount())
                .paymentMethod(paymentMethod)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            // Call payment service
            var paymentResponse = paymentServiceClient.processPayment(paymentRequest);

            // Extract payment ID and status
            Object paymentIdObj = paymentResponse.get("id");
            Object statusObj = paymentResponse.get("status");

            if (paymentIdObj == null || statusObj == null) {
                throw new PaymentFailedException("Invalid payment response");
            }

            Long paymentId = paymentIdObj instanceof Number ? ((Number) paymentIdObj).longValue()
                    : Long.parseLong(paymentIdObj.toString());

            String status = statusObj.toString();

            if ("SUCCESS".equals(status) || "COMPLETED".equals(status)) {
                // Payment successful, update order
                order.setStatus(OrderStatus.CONFIRMED);
                order.setPaymentId(paymentId);
                Order updatedOrder = orderRepository.save(order);

                log.info("Payment successful for order: {}. Payment ID: {}", orderId, paymentId);
                return mapToOrderResponse(updatedOrder);
            } else {
                // Payment failed, release inventory
                log.warn("Payment failed for order: {}. Status: {}", orderId, status);
                releaseInventory(orderId);
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
                throw new PaymentFailedException("Payment failed with status: " + status);
            }

        } catch (PaymentFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Payment processing failed for order {}: {}", orderId, e.getMessage());
            // Compensate: Release inventory
            releaseInventory(orderId);
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            throw new PaymentFailedException("Payment processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Cancel an order
     * Compensating transaction:
     * 1. Update order status to CANCELLED
     * 2. Release reserved inventory
     */
    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long userId) {
        log.info("Cancelling order: {} by user: {}", orderId, userId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Verify ownership
        if (!order.getUserId().equals(userId)) {
            throw new UnauthorizedOrderAccessException(orderId, userId);
        }

        // Check if order can be cancelled
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderException("Order is already cancelled");
        }

        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.SHIPPED) {
            throw new OrderException("Cannot cancel order in " + order.getStatus() + " status");
        }

        // Release inventory
        try {
            releaseInventory(orderId);
        } catch (Exception e) {
            log.warn("Failed to release inventory for order {}: {}", orderId, e.getMessage());
            // Continue with cancellation even if release fails
        }

        // Update order status
        order.setStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);

        log.info("Order {} cancelled successfully", orderId);
        return mapToOrderResponse(updatedOrder);
    }

    /**
     * Get order by ID
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, Long userId) {
        log.debug("Fetching order: {} for user: {}", orderId, userId);

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Verify ownership
        if (!order.getUserId().equals(userId)) {
            throw new UnauthorizedOrderAccessException(orderId, userId);
        }

        return mapToOrderResponse(order);
    }

    /**
     * Get all orders for a user
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(Long userId) {
        log.debug("Fetching all orders for user: {}", userId);

        List<Order> orders = orderRepository.findByUserIdWithItems(userId);
        return orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get orders by status for a user
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrdersByStatus(Long userId, OrderStatus status) {
        log.debug("Fetching orders with status {} for user: {}", status, userId);

        List<Order> orders = orderRepository.findByUserIdAndStatus(userId, status);
        return orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    // Helper methods

    private ReserveInventoryRequest buildReserveInventoryRequest(Order order) {
        List<ReserveInventoryRequest.InventoryItem> items = order.getItems().stream()
                .map(orderItem -> ReserveInventoryRequest.InventoryItem.builder()
                        .productId(orderItem.getProductId())
                        .quantity(orderItem.getQuantity())
                        .build())
                .collect(Collectors.toList());

        return ReserveInventoryRequest.builder()
                .orderId(order.getId())
                .items(items)
                .build();
    }

    private void releaseInventory(Long orderId) {
        try {
            inventoryServiceClient.releaseInventory(orderId);
            log.info("Inventory released for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to release inventory for order {}: {}", orderId, e.getMessage());
            throw new OrderException("Failed to release inventory", e);
        }
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }

    // Circuit breaker fallback methods

    public OrderResponse createOrderFallback(CreateOrderRequest request, Long userId, Exception e) {
        log.error("Circuit breaker activated for createOrder. Error: {}", e.getMessage());
        throw new OrderException("Order service is temporarily unavailable. Please try again later.", e);
    }

    public OrderResponse processPaymentFallback(Long orderId, String paymentMethod, Long userId, Exception e) {
        log.error("Circuit breaker activated for processPayment. Error: {}", e.getMessage());
        throw new PaymentFailedException("Payment service is temporarily unavailable. Please try again later.", e);
    }
}
