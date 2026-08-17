package com.vv456.Inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAvailabilityCheckResponse {
    // Map of productId -> available (boolean)
    private Map<Long, Boolean> availability;
}
