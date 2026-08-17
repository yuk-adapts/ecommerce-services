package com.vv456.cart_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private String reservationId;
    
    private String userId;
    
    private Boolean success;
    
    private String message;
    
    private LocalDateTime expiresAt;
}

