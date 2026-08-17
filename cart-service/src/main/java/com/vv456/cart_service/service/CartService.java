package com.vv456.cart_service.service;

import com.vv456.cart_service.client.InventoryClient;
import com.vv456.cart_service.dto.*;
import com.vv456.cart_service.exception.*;
import com.vv456.cart_service.model.Cart;
import com.vv456.cart_service.model.CartItem;
import com.vv456.cart_service.repository.CartCacheRepository;
import com.vv456.cart_service.repository.CartRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartCacheRepository cartCacheRepository;
    private final InventoryClient inventoryClient;

    public CartResponse getCart(String userId) {
        log.info("Getting cart for user: {}", userId);

        Cart cart = cartCacheRepository.getCart(userId)
                .orElseGet(() -> {
                    Cart dbCart = cartRepository.findByUserId(userId)
                            .orElseGet(() -> createNewCart(userId));
                    cartCacheRepository.saveCart(dbCart);
                    return dbCart;
                });

        return toCartResponse(cart, "Cart retrieved successfully");
    }

    @Transactional
    public CartResponse addItemToCart(String userId, AddToCartRequest request) {
        log.info("Adding item to cart. User: {}, Product: {}, Quantity: {}",
                userId, request.getProductId(), request.getQuantity());

        InventoryCheckResponse productInfo;
        try {
            productInfo = inventoryClient.checkProductAvailability(request.getProductId());
        } catch (FeignException e) {
            log.error("Failed to check inventory for product: {}", request.getProductId(), e);
            throw new InventoryServiceException("Unable to verify product availability. Please try again later.");
        }

        if (productInfo == null || !productInfo.getAvailable()) {
            throw new ProductNotAvailableException("Product is currently not available");
        }

        if (productInfo.getAvailableStock() < request.getQuantity()) {
            throw new InsufficientStockException(
                    String.format("Only %d units available", productInfo.getAvailableStock()),
                    productInfo.getAvailableStock());
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> createNewCart(userId));

        CartItem cartItem = CartItem.builder()
                .productId(request.getProductId())
                .productName(productInfo.getProductName())
                .price(productInfo.getPrice())
                .quantity(request.getQuantity())
                .imageUrl(productInfo.getImageUrl())
                .addedAt(LocalDateTime.now())
                .available(true)
                .availableStock(productInfo.getAvailableStock())
                .build();

        cart.addItem(cartItem);
        cart.setExpiresAt(LocalDateTime.now().plusDays(30));

        Cart savedCart = cartRepository.save(cart);
        cartCacheRepository.saveCart(savedCart);

        log.info("Item added to cart successfully for user: {}", userId);
        return toCartResponse(savedCart, "Item added to cart successfully");
    }

    @Transactional
    public CartResponse updateCartItem(String userId, String productId, UpdateCartItemRequest request) {
        log.info("Updating cart item. User: {}, Product: {}, New Quantity: {}",
                userId, productId, request.getQuantity());

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

        boolean productExists = cart.getItems().stream()
                .anyMatch(item -> item.getProductId().equals(productId));

        if (!productExists) {
            throw new ProductNotAvailableException("Product not found in cart");
        }

        try {
            InventoryCheckResponse productInfo = inventoryClient.checkProductAvailability(productId);

            if (!productInfo.getAvailable()) {
                throw new ProductNotAvailableException("Product is no longer available");
            }

            if (productInfo.getAvailableStock() < request.getQuantity()) {
                throw new InsufficientStockException(
                        String.format("Only %d units available", productInfo.getAvailableStock()),
                        productInfo.getAvailableStock());
            }
        } catch (FeignException e) {
            log.warn("Inventory service unavailable. Updating cart optimistically.", e);
        }

        cart.updateItemQuantity(productId, request.getQuantity());

        Cart savedCart = cartRepository.save(cart);
        cartCacheRepository.saveCart(savedCart);

        log.info("Cart item updated successfully for user: {}", userId);
        return toCartResponse(savedCart, "Cart item updated successfully");
    }

    @Transactional
    public CartResponse removeCartItem(String userId, String productId) {
        log.info("Removing item from cart. User: {}, Product: {}", userId, productId);

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

        cart.removeItem(productId);

        Cart savedCart = cartRepository.save(cart);
        cartCacheRepository.saveCart(savedCart);

        log.info("Item removed from cart for user: {}", userId);
        return toCartResponse(savedCart, "Item removed from cart");
    }

    @Transactional
    public void clearCart(String userId) {
        log.info("Clearing cart for user: {}", userId);

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

        cart.clearCart();
        cartRepository.save(cart);
        cartCacheRepository.deleteCart(userId);

        log.info("Cart cleared for user: {}", userId);
    }

    public ReservationResponse reserveCartItems(String userId) {
        log.info("Reserving cart items for user: {}", userId);

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

        if (cart.getItems().isEmpty()) {
            throw new ProductNotAvailableException("Cart is empty");
        }

        List<ReservationRequest.ReservationItem> reservationItems = cart.getItems().stream()
                .map(item -> ReservationRequest.ReservationItem.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        ReservationRequest request = ReservationRequest.builder()
                .userId(userId)
                .items(reservationItems)
                .build();

        try {
            return inventoryClient.reserveItems(request);
        } catch (FeignException e) {
            log.error("Failed to reserve items for user: {}", userId, e);
            throw new InventoryServiceException("Unable to reserve items. Please try again.");
        }
    }

    public CartResponse validateCart(String userId) {
        log.info("Validating cart for user: {}", userId);

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

        boolean cartModified = false;

        for (CartItem item : cart.getItems()) {
            try {
                InventoryCheckResponse productInfo = inventoryClient.checkProductAvailability(item.getProductId());

                item.setAvailable(productInfo.getAvailable());
                item.setAvailableStock(productInfo.getAvailableStock());

                if (!item.getPrice().equals(productInfo.getPrice())) {
                    item.setPrice(productInfo.getPrice());
                    cartModified = true;
                }

                if (item.getQuantity() > productInfo.getAvailableStock()) {
                    item.setQuantity(productInfo.getAvailableStock());
                    cartModified = true;
                }

            } catch (FeignException e) {
                log.warn("Failed to validate product: {}", item.getProductId(), e);
                item.setAvailable(false);
            }
        }

        if (cartModified) {
            Cart finalCart = cart;
            cart.getItems().forEach(item -> finalCart.updateItemQuantity(item.getProductId(), item.getQuantity()));
            cart = cartRepository.save(cart);
            cartCacheRepository.saveCart(cart);
        }

        return toCartResponse(cart, "Cart validated successfully");
    }

    private Cart createNewCart(String userId) {
        log.info("Creating new cart for user: {}", userId);

        Cart cart = Cart.builder()
                .userId(userId)
                .items(new ArrayList<>())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        return cartRepository.save(cart);
    }

    private CartResponse toCartResponse(Cart cart, String message) {
        return CartResponse.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .items(cart.getItems())
                .totalPrice(cart.getTotalPrice())
                .totalItems(cart.getTotalItems())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .message(message)
                .build();
    }
}
