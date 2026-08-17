package com.vv456.cart_service.controller;

import com.vv456.cart_service.dto.*;
import com.vv456.cart_service.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cart", description = "Cart management APIs")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get user's cart", description = "Retrieve the current user's shopping cart")
    public ResponseEntity<CartResponse> getCart(Authentication authentication) {
        String userId = authentication.getName();
        log.info("GET /api/cart - User: {}", userId);

        CartResponse response = cartService.getCart(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/items")
    @Operation(summary = "Add item to cart", description = "Add a product to the shopping cart")
    public ResponseEntity<CartResponse> addItemToCart(
            @Valid @RequestBody AddToCartRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        log.info("POST /api/cart/items - User: {}, Request: {}", userId, request);

        CartResponse response = cartService.addItemToCart(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Update cart item", description = "Update the quantity of a cart item")
    public ResponseEntity<CartResponse> updateCartItem(
            @Parameter(description = "Product ID", required = true) @PathVariable("productId") String productId,
            @Valid @RequestBody UpdateCartItemRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        log.info("PUT /api/cart/items/{} - User: {}, Request: {}", productId, userId, request);

        CartResponse response = cartService.updateCartItem(userId, productId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove cart item", description = "Remove a product from the cart")
    public ResponseEntity<CartResponse> removeCartItem(
            @Parameter(description = "Product ID", required = true) @PathVariable("productId") String productId,
            Authentication authentication) {
        String userId = authentication.getName();
        log.info("DELETE /api/cart/items/{} - User: {}", productId, userId);

        CartResponse response = cartService.removeCartItem(userId, productId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    @Operation(summary = "Clear cart", description = "Remove all items from the cart")
    public ResponseEntity<Void> clearCart(Authentication authentication) {
        String userId = authentication.getName();
        log.info("DELETE /api/cart - User: {}", userId);

        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate cart", description = "Validate cart items availability and update prices")
    public ResponseEntity<CartResponse> validateCart(Authentication authentication) {
        String userId = authentication.getName();
        log.info("POST /api/cart/validate - User: {}", userId);

        CartResponse response = cartService.validateCart(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reserve")
    @Operation(summary = "Reserve cart items", description = "Reserve cart items for checkout (called by order service)")
    public ResponseEntity<ReservationResponse> reserveCartItems(Authentication authentication) {
        String userId = authentication.getName();
        log.info("POST /api/cart/reserve - User: {}", userId);

        ReservationResponse response = cartService.reserveCartItems(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if cart service is running")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Cart Service is running!");
    }
}
