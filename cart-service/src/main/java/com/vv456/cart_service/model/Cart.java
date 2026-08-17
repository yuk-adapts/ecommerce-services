package com.vv456.cart_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "carts")
public class Cart implements Serializable {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @Builder.Default
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Builder.Default
    private Integer totalItems = 0;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Indexed(expireAfterSeconds = 2592000)
    private LocalDateTime expiresAt;

    public void addItem(CartItem item) {
        boolean found = false;
        for (CartItem existingItem : items) {
            if (existingItem.getProductId().equals(item.getProductId())) {
                existingItem.setQuantity(existingItem.getQuantity() + item.getQuantity());
                found = true;
                break;
            }
        }
        if (!found) {
            items.add(item);
        }
        recalculateTotal();
    }

    public void updateItemQuantity(String productId, Integer quantity) {
        items.stream()
            .filter(item -> item.getProductId().equals(productId))
            .findFirst()
            .ifPresent(item -> item.setQuantity(quantity));
        recalculateTotal();
    }

    public void removeItem(String productId) {
        items.removeIf(item -> item.getProductId().equals(productId));
        recalculateTotal();
    }

    public void clearCart() {
        items.clear();
        totalPrice = BigDecimal.ZERO;
        totalItems = 0;
    }

    private void recalculateTotal() {
        totalPrice = items.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        totalItems = items.stream()
            .mapToInt(CartItem::getQuantity)
            .sum();
    }
}

