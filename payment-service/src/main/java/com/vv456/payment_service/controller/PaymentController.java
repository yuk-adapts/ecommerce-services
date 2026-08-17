package com.vv456.payment_service.controller;

import com.vv456.payment_service.dto.*;
import com.vv456.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment processing and refund management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class PaymentController {

        private final PaymentService paymentService;

        @Operation(summary = "Process payment", description = "Processes a payment for an order with full idempotency support. Called by Order Service during SAGA orchestration.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Payment processed successfully", content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Payment processing failed", content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
        })
        @PostMapping
        public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest request) {
                log.info("Received payment request for order: {}", request.getOrderId());

                PaymentResponse response = paymentService.processPayment(request);

                if (response.isSuccess()) {
                        return ResponseEntity.status(HttpStatus.CREATED).body(response);
                } else {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
        }

        @Operation(summary = "Get payment by ID", description = "Retrieves payment details by payment ID")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Payment found", content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
                        @ApiResponse(responseCode = "404", description = "Payment not found")
        })
        @GetMapping("/{id}")
        public ResponseEntity<PaymentResponse> getPaymentById(
                        @Parameter(description = "Payment ID", required = true) @PathVariable("id") Long id) {
                log.info("Fetching payment with id: {}", id);
                PaymentResponse response = paymentService.getPaymentById(id);
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Get payments by order ID", description = "Retrieves all payments associated with a specific order. Used by Order Service to check payment status.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Payments retrieved successfully", content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
        })
        @GetMapping("/order/{orderId}")
        public ResponseEntity<List<PaymentResponse>> getPaymentsByOrderId(
                        @Parameter(description = "Order ID", required = true) @PathVariable("orderId") Long orderId) {
                log.info("Fetching payments for order: {}", orderId);
                List<PaymentResponse> responses = paymentService.getPaymentsByOrderId(orderId);
                return ResponseEntity.ok(responses);
        }

        @Operation(summary = "Get payment status", description = "Checks the current status of a payment. Used for timeout recovery - Order Service can poll this endpoint.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Payment status retrieved", content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
                        @ApiResponse(responseCode = "404", description = "Payment not found")
        })
        @GetMapping("/{id}/status")
        public ResponseEntity<PaymentResponse> getPaymentStatus(
                        @Parameter(description = "Payment ID", required = true) @PathVariable("id") Long id) {
                log.info("Checking payment status for id: {}", id);
                PaymentResponse response = paymentService.getPaymentStatus(id);
                return ResponseEntity.ok(response);
        }

        @Operation(summary = "Process refund", description = "Processes a refund for a payment. Used when an order is cancelled or partially cancelled.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Refund processed successfully", content = @Content(schema = @Schema(implementation = RefundResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Refund processing failed", content = @Content(schema = @Schema(implementation = RefundResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
        })
        @PostMapping("/{id}/refund")
        public ResponseEntity<RefundResponse> processRefund(
                        @Parameter(description = "Payment ID", required = true) @PathVariable("id") Long id,
                        @Valid @RequestBody RefundRequest request) {

                log.info("Received refund request for payment: {}", id);
                request.setPaymentId(id);

                RefundResponse response = paymentService.processRefund(request);

                if (response.isSuccess()) {
                        return ResponseEntity.status(HttpStatus.CREATED).body(response);
                } else {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
        }

        @Operation(summary = "Get refunds by payment ID", description = "Retrieves all refunds associated with a specific payment")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Refunds retrieved successfully", content = @Content(schema = @Schema(implementation = RefundResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
        })
        @GetMapping("/{id}/refunds")
        public ResponseEntity<List<RefundResponse>> getRefundsByPaymentId(
                        @Parameter(description = "Payment ID", required = true) @PathVariable("id") Long id) {
                log.info("Fetching refunds for payment: {}", id);
                List<RefundResponse> responses = paymentService.getRefundsByPaymentId(id);
                return ResponseEntity.ok(responses);
        }

        @Operation(summary = "Health check", description = "Simple health check endpoint to verify service availability")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Service is healthy")
        })
        @GetMapping("/health")
        public ResponseEntity<String> health() {
                return ResponseEntity.ok("Payment Service is healthy");
        }
}
