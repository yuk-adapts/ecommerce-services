package com.vv456.Inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer availableQuantity;
    private boolean inStock;
}

