package com.vv456.order_service.client;

import com.vv456.order_service.dto.ProcessPaymentRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "payment-service")
public interface PaymentServiceClient {

    @PostMapping("/api/payments")
    Map<String, Object> processPayment(@RequestBody ProcessPaymentRequest request);
}
