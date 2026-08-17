package com.vv456.payment_service.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentGatewayResponse {

    private boolean success;
    private String transactionId;
    private String message;
    private String errorCode;
    private String rawResponse;

    public static PaymentGatewayResponse success(String transactionId) {
        return PaymentGatewayResponse.builder()
                .success(true)
                .transactionId(transactionId)
                .message("Payment processed successfully")
                .build();
    }

    public static PaymentGatewayResponse failure(String errorCode, String message) {
        return PaymentGatewayResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}

