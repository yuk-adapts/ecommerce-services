package com.vv456.cart_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem implements Serializable {

    private String productId;
    
    private String productName;
    
    private BigDecimal price;
    
    private Integer quantity;
    
    private String imageUrl;
    
    private LocalDateTime addedAt;
    
    private Boolean available;  // Track if product is still available
    
    private Integer availableStock;  // Current available stock from inventory
}

