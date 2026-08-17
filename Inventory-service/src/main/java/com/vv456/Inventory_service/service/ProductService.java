package com.vv456.Inventory_service.service;

import com.vv456.Inventory_service.dto.BatchAvailabilityCheckRequest;
import com.vv456.Inventory_service.dto.BatchAvailabilityCheckResponse;
import com.vv456.Inventory_service.dto.ProductListResponse;
import com.vv456.Inventory_service.dto.ProductResponse;
import com.vv456.Inventory_service.entities.Inventory;
import com.vv456.Inventory_service.entities.Product;
import com.vv456.Inventory_service.repository.InventoryRepository;
import com.vv456.Inventory_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    /**
     * Get all products with basic inventory info
     */
    @Transactional(readOnly = true)
    public List<ProductListResponse> getAllProducts() {
        log.info("Fetching all products");
        List<Product> products = productRepository.findAll();

        return products.stream()
                .map(this::mapToProductListResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get product by ID with detailed inventory info
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long productId) {
        log.info("Fetching product with ID: {}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + productId));

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElse(null);

        return mapToProductResponse(product, inventory);
    }

    /**
     * Check if product is available in requested quantity
     */
    @Transactional(readOnly = true)
    public Boolean checkAvailability(Long productId, Integer quantity) {
        log.info("Checking availability for productId: {}, quantity: {}", productId, quantity);

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElse(null);

        if (inventory == null) {
            log.warn("Product not found: {}", productId);
            return false;
        }

        boolean available = inventory.getAvailableQuantity() >= quantity;
        log.info("Product {} availability check: {} (available: {}, requested: {})",
                productId, available, inventory.getAvailableQuantity(), quantity);

        return available;
    }

    /**
     * Get product with inventory details
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductWithInventory(Long productId) {
        log.info("Fetching product with inventory for ID: {}", productId);
        return getProductById(productId);
    }

    /**
     * Get multiple products by IDs with detailed inventory info
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByIds(List<Long> productIds) {
        log.info("Fetching products by IDs: {}", productIds);

        List<Product> products = productRepository.findByIdIn(productIds);
        List<Inventory> inventories = inventoryRepository.findByProductIdIn(productIds);

        // Create a map for quick lookup
        Map<Long, Inventory> inventoryMap = inventories.stream()
                .collect(Collectors.toMap(Inventory::getProductId, inv -> inv));

        return products.stream()
                .map(product -> mapToProductResponse(product, inventoryMap.get(product.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Batch check availability for multiple products
     */
    @Transactional(readOnly = true)
    public BatchAvailabilityCheckResponse checkBatchAvailability(BatchAvailabilityCheckRequest request) {
        log.info("Batch checking availability for {} items", request.getItems().size());

        List<Long> productIds = request.getItems().stream()
                .map(BatchAvailabilityCheckRequest.AvailabilityItem::getProductId)
                .collect(Collectors.toList());

        List<Inventory> inventories = inventoryRepository.findByProductIdIn(productIds);
        Map<Long, Inventory> inventoryMap = inventories.stream()
                .collect(Collectors.toMap(Inventory::getProductId, inv -> inv));

        Map<Long, Integer> quantityMap = request.getItems().stream()
                .collect(Collectors.toMap(
                        BatchAvailabilityCheckRequest.AvailabilityItem::getProductId,
                        BatchAvailabilityCheckRequest.AvailabilityItem::getQuantity));

        Map<Long, Boolean> availabilityMap = new HashMap<>();
        for (BatchAvailabilityCheckRequest.AvailabilityItem item : request.getItems()) {
            Inventory inventory = inventoryMap.get(item.getProductId());
            boolean available = inventory != null && inventory.getAvailableQuantity() >= item.getQuantity();
            availabilityMap.put(item.getProductId(), available);

            log.debug("Product {} availability check: {} (available: {}, requested: {})",
                    item.getProductId(), available,
                    inventory != null ? inventory.getAvailableQuantity() : 0,
                    item.getQuantity());
        }

        return BatchAvailabilityCheckResponse.builder()
                .availability(availabilityMap)
                .build();
    }

    // ============= Helper Methods =============

    private ProductListResponse mapToProductListResponse(Product product) {
        Inventory inventory = product.getInventory();

        return ProductListResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .availableQuantity(inventory != null ? inventory.getAvailableQuantity() : 0)
                .inStock(inventory != null && inventory.getAvailableQuantity() > 0)
                .build();
    }

    private ProductResponse mapToProductResponse(Product product, Inventory inventory) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .availableQuantity(inventory != null ? inventory.getAvailableQuantity() : 0)
                .reservedQuantity(inventory != null ? inventory.getReservedQuantity() : 0)
                .build();
    }
}
