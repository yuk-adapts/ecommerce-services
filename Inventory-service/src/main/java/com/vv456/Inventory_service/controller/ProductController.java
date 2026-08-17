package com.vv456.Inventory_service.controller;

import com.vv456.Inventory_service.dto.BatchProductRequest;
import com.vv456.Inventory_service.dto.ProductListResponse;
import com.vv456.Inventory_service.dto.ProductResponse;
import com.vv456.Inventory_service.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product Catalog", description = "Product browsing and information APIs")
public class ProductController {

        private final ProductService productService;

        @Operation(summary = "Get all products", description = "Retrieves a list of all available products with basic inventory information. Public endpoint for product browsing.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Products retrieved successfully", content = @Content(schema = @Schema(implementation = ProductListResponse.class)))
        })
        @GetMapping
        public ResponseEntity<List<ProductListResponse>> getAllProducts() {
                log.info("Get all products request");
                List<ProductListResponse> products = productService.getAllProducts();
                return ResponseEntity.ok(products);
        }

        @Operation(summary = "Get product by ID", description = "Retrieves detailed information about a specific product including full inventory details")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Product found", content = @Content(schema = @Schema(implementation = ProductResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Product not found")
        })
        @GetMapping("/{productId}")
        public ResponseEntity<ProductResponse> getProductById(
                        @Parameter(description = "Product ID", required = true) @PathVariable("productId") Long productId) {
                log.info("Get product by ID request: {}", productId);

                try {
                        ProductResponse product = productService.getProductById(productId);
                        return ResponseEntity.ok(product);
                } catch (RuntimeException e) {
                        log.error("Product not found: {}", productId);
                        return ResponseEntity.notFound().build();
                }
        }

        @Operation(summary = "Get product with inventory details", description = "Retrieves product information along with detailed inventory status (available and reserved quantities)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Product with inventory retrieved successfully", content = @Content(schema = @Schema(implementation = ProductResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Product not found")
        })
        @GetMapping("/{productId}/inventory")
        public ResponseEntity<ProductResponse> getProductWithInventory(
                        @Parameter(description = "Product ID", required = true) @PathVariable("productId") Long productId) {
                log.info("Get product with inventory request: {}", productId);

                try {
                        ProductResponse product = productService.getProductWithInventory(productId);
                        return ResponseEntity.ok(product);
                } catch (RuntimeException e) {
                        log.error("Product not found: {}", productId);
                        return ResponseEntity.notFound().build();
                }
        }

        @Operation(summary = "Get multiple products by IDs", description = "Retrieves detailed information about multiple products by their IDs in a single request. Used for batch operations.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Products retrieved successfully", content = @Content(schema = @Schema(implementation = ProductResponse.class)))
        })
        @PostMapping("/batch")
        public ResponseEntity<List<ProductResponse>> getProductsByIds(
                        @RequestBody BatchProductRequest request) {
                log.info("Get products by IDs batch request: {}", request.getProductIds());
                List<ProductResponse> products = productService.getProductsByIds(request.getProductIds());
                return ResponseEntity.ok(products);
        }
}
