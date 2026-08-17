package com.vv456.order_service.service;

import com.vv456.order_service.client.InventoryServiceClient;
import com.vv456.order_service.client.PaymentServiceClient;
import com.vv456.order_service.dto.*;
import com.vv456.order_service.entities.Order;
import com.vv456.order_service.entities.OrderItem;
import com.vv456.order_service.enums.OrderStatus;
import com.vv456.order_service.exception.InsufficientInventoryException;
import com.vv456.order_service.exception.OrderNotFoundException;
import com.vv456.order_service.exception.UnauthorizedOrderAccessException;
import com.vv456.order_service.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryServiceClient inventoryServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    @InjectMocks
    private OrderService orderService;

    private Long userId;
    private Long productId;
    private CreateOrderRequest createOrderRequest;
    private ProductResponse productResponse;
    private Order order;

    @BeforeEach
    void setUp() {
        userId = 1L;
        productId = 100L;

        // Setup create order request
        OrderItemRequest itemRequest = OrderItemRequest.builder()
                .productId(productId)
                .quantity(2)
                .build();

        createOrderRequest = CreateOrderRequest.builder()
                .items(Collections.singletonList(itemRequest))
                .build();

        // Setup product response
        productResponse = ProductResponse.builder()
                .id(productId)
                .name("Test Product")
                .description("Test Description")
                .price(BigDecimal.valueOf(100.00))
                .category("Electronics")
                .brand("TestBrand")
                .build();

        // Setup order entity
        OrderItem orderItem = OrderItem.builder()
                .id(1L)
                .productId(productId)
                .productName("Test Product")
                .quantity(2)
                .price(BigDecimal.valueOf(100.00))
                .subtotal(BigDecimal.valueOf(200.00))
                .build();

        order = Order.builder()
                .id(1L)
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(200.00))
                .items(Collections.singletonList(orderItem))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        orderItem.setOrder(order);
    }

    @Test
    void testCreateOrder_Success() {
        // Given
        when(inventoryServiceClient.getProduct(productId)).thenReturn(productResponse);
        when(inventoryServiceClient.checkAvailability(productId, 2)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(inventoryServiceClient.reserveInventory(any(ReserveInventoryRequest.class))).thenReturn(true);

        // When
        OrderResponse response = orderService.createOrder(createOrderRequest, userId);

        // Then
        assertNotNull(response);
        assertEquals(order.getId(), response.getId());
        assertEquals(userId, response.getUserId());
        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertEquals(BigDecimal.valueOf(200.00), response.getTotalAmount());
        assertEquals(1, response.getItems().size());

        verify(inventoryServiceClient).getProduct(productId);
        verify(inventoryServiceClient).checkAvailability(productId, 2);
        verify(orderRepository).save(any(Order.class));
        verify(inventoryServiceClient).reserveInventory(any(ReserveInventoryRequest.class));
    }

    @Test
    void testCreateOrder_InsufficientInventory() {
        // Given
        when(inventoryServiceClient.getProduct(productId)).thenReturn(productResponse);
        when(inventoryServiceClient.checkAvailability(productId, 2)).thenReturn(false);

        // When & Then
        assertThrows(InsufficientInventoryException.class, () -> {
            orderService.createOrder(createOrderRequest, userId);
        });

        verify(inventoryServiceClient).getProduct(productId);
        verify(inventoryServiceClient).checkAvailability(productId, 2);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void testProcessPayment_Success() {
        // Given
        Long orderId = 1L;
        String paymentMethod = "CREDIT_CARD";
        
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        
        Map<String, Object> paymentResponse = new HashMap<>();
        paymentResponse.put("id", 1L);
        paymentResponse.put("status", "SUCCESS");
        
        when(paymentServiceClient.processPayment(any(ProcessPaymentRequest.class)))
                .thenReturn(paymentResponse);
        
        Order confirmedOrder = Order.builder()
                .id(orderId)
                .userId(userId)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(BigDecimal.valueOf(200.00))
                .paymentId(1L)
                .items(order.getItems())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        when(orderRepository.save(any(Order.class))).thenReturn(confirmedOrder);

        // When
        OrderResponse response = orderService.processPayment(orderId, paymentMethod, userId);

        // Then
        assertNotNull(response);
        assertEquals(OrderStatus.CONFIRMED, response.getStatus());

        verify(orderRepository).findByIdWithItems(orderId);
        verify(paymentServiceClient).processPayment(any(ProcessPaymentRequest.class));
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void testProcessPayment_OrderNotFound() {
        // Given
        Long orderId = 999L;
        String paymentMethod = "CREDIT_CARD";
        
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.processPayment(orderId, paymentMethod, userId);
        });

        verify(orderRepository).findByIdWithItems(orderId);
        verify(paymentServiceClient, never()).processPayment(any());
    }

    @Test
    void testProcessPayment_UnauthorizedAccess() {
        // Given
        Long orderId = 1L;
        Long differentUserId = 999L;
        String paymentMethod = "CREDIT_CARD";
        
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        // When & Then
        assertThrows(UnauthorizedOrderAccessException.class, () -> {
            orderService.processPayment(orderId, paymentMethod, differentUserId);
        });

        verify(orderRepository).findByIdWithItems(orderId);
        verify(paymentServiceClient, never()).processPayment(any());
    }

    @Test
    void testCancelOrder_Success() {
        // Given
        Long orderId = 1L;
        
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        doNothing().when(inventoryServiceClient).releaseInventory(orderId);
        
        Order cancelledOrder = Order.builder()
                .id(orderId)
                .userId(userId)
                .status(OrderStatus.CANCELLED)
                .totalAmount(order.getTotalAmount())
                .items(order.getItems())
                .createdAt(order.getCreatedAt())
                .updatedAt(Instant.now())
                .build();
        
        when(orderRepository.save(any(Order.class))).thenReturn(cancelledOrder);

        // When
        OrderResponse response = orderService.cancelOrder(orderId, userId);

        // Then
        assertNotNull(response);
        assertEquals(OrderStatus.CANCELLED, response.getStatus());

        verify(orderRepository).findById(orderId);
        verify(inventoryServiceClient).releaseInventory(orderId);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void testGetOrderById_Success() {
        // Given
        Long orderId = 1L;
        
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        // When
        OrderResponse response = orderService.getOrderById(orderId, userId);

        // Then
        assertNotNull(response);
        assertEquals(orderId, response.getId());
        assertEquals(userId, response.getUserId());
        assertEquals(OrderStatus.PENDING, response.getStatus());

        verify(orderRepository).findByIdWithItems(orderId);
    }

    @Test
    void testGetOrderById_NotFound() {
        // Given
        Long orderId = 999L;
        
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.getOrderById(orderId, userId);
        });

        verify(orderRepository).findByIdWithItems(orderId);
    }

    @Test
    void testGetOrderById_UnauthorizedAccess() {
        // Given
        Long orderId = 1L;
        Long differentUserId = 999L;
        
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        // When & Then
        assertThrows(UnauthorizedOrderAccessException.class, () -> {
            orderService.getOrderById(orderId, differentUserId);
        });

        verify(orderRepository).findByIdWithItems(orderId);
    }

    @Test
    void testGetUserOrders_Success() {
        // Given
        List<Order> orders = Arrays.asList(order);
        
        when(orderRepository.findByUserIdWithItems(userId)).thenReturn(orders);

        // When
        List<OrderResponse> responses = orderService.getUserOrders(userId);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(order.getId(), responses.get(0).getId());

        verify(orderRepository).findByUserIdWithItems(userId);
    }

    @Test
    void testGetUserOrdersByStatus_Success() {
        // Given
        OrderStatus status = OrderStatus.PENDING;
        List<Order> orders = Arrays.asList(order);
        
        when(orderRepository.findByUserIdAndStatus(userId, status)).thenReturn(orders);

        // When
        List<OrderResponse> responses = orderService.getUserOrdersByStatus(userId, status);

        // Then
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(order.getId(), responses.get(0).getId());
        assertEquals(status, responses.get(0).getStatus());

        verify(orderRepository).findByUserIdAndStatus(userId, status);
    }
}


