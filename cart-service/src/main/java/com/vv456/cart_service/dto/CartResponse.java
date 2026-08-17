package com.vv456.cart_service.dto;

import com.vv456.cart_service.model.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private String cartId;
    
    private String userId;
    
    private List<CartItem> items;
    
    private BigDecimal totalPrice;
    
    private Integer totalItems;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private String message;  // For feedback messages
}

