package com.vv456.Inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckAndReserveResponse {
    private boolean success;
    private String message;
    private Long productId;
    private Integer requestedQuantity;
    private Integer availableQuantity;
    private Integer reservedQuantity;
}
