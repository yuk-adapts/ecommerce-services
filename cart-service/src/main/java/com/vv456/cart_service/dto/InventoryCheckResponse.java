package com.vv456.cart_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCheckResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    private BigDecimal price;

    @JsonProperty("availableQuantity")
    private Integer availableQuantity;

    @JsonProperty("reservedQuantity")
    private Integer reservedQuantity;

    public String getProductId() {
        return id != null ? String.valueOf(id) : null;
    }

    public String getProductName() {
        return name;
    }

    public Integer getAvailableStock() {
        return availableQuantity;
    }

    public Boolean getAvailable() {
        return availableQuantity != null && availableQuantity > 0;
    }

    public String getImageUrl() {
        return null;
    }
}
