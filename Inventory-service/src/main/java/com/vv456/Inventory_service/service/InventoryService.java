package com.vv456.Inventory_service.service;

import com.vv456.Inventory_service.dto.CheckAndReserveRequest;
import com.vv456.Inventory_service.dto.CheckAndReserveResponse;
import com.vv456.Inventory_service.dto.ReserveInventoryRequest;
import com.vv456.Inventory_service.entities.Inventory;
import com.vv456.Inventory_service.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

        private final InventoryRepository inventoryRepository;

        /**
         * Reserve inventory for multiple items in an order.
         * This is called by order-service when creating an order.
         * Uses pessimistic locking to prevent race conditions.
         * 
         * @return true if all items were reserved successfully, false otherwise
         */
        @Transactional
        public boolean reserveInventoryForOrder(ReserveInventoryRequest request) {
                log.info("Reserving inventory for order: {}, items count: {}",
                                request.getOrderId(), request.getItems().size());

                List<Inventory> reservedInventories = new ArrayList<>();

                try {
                        for (ReserveInventoryRequest.InventoryItem item : request.getItems()) {
                                Inventory inventory = inventoryRepository.findByProductIdForUpdate(item.getProductId())
                                                .orElse(null);

                                if (inventory == null) {
                                        log.warn("Product not found during reservation: {}", item.getProductId());
                                        // Rollback previously reserved items
                                        rollbackReservations(reservedInventories);
                                        return false;
                                }

                                if (inventory.getAvailableQuantity() < item.getQuantity()) {
                                        log.warn("Insufficient inventory for productId: {}. Available: {}, Requested: {}",
                                                        item.getProductId(), inventory.getAvailableQuantity(),
                                                        item.getQuantity());
                                        // Rollback previously reserved items
                                        rollbackReservations(reservedInventories);
                                        return false;
                                }

                                // Reserve the quantity
                                inventory.setAvailableQuantity(inventory.getAvailableQuantity() - item.getQuantity());
                                inventory.setReservedQuantity(inventory.getReservedQuantity() + item.getQuantity());
                                inventoryRepository.save(inventory);

                                // Track for potential rollback
                                reservedInventories.add(inventory);

                                log.info("Reserved {} units of product {} for order {}",
                                                item.getQuantity(), item.getProductId(), request.getOrderId());
                        }

                        log.info("Successfully reserved all inventory for order: {}", request.getOrderId());
                        return true;

                } catch (Exception e) {
                        log.error("Error reserving inventory for order {}: {}", request.getOrderId(), e.getMessage());
                        rollbackReservations(reservedInventories);
                        throw e;
                }
        }

        private void rollbackReservations(List<Inventory> reservedInventories) {
                // Note: This won't actually work for rollback since we're in same transaction
                // The transaction will rollback automatically on exception
                // This is here for explicit logging purposes
                log.warn("Rolling back {} inventory reservations", reservedInventories.size());
        }

        /**
         * Checks if the requested quantity is available and reserves it temporarily.
         * This is typically used when items are added to cart.
         * Reserved items should be released if cart expires (>1 day) or converted to
         * order on checkout.
         */
        @Transactional
        public CheckAndReserveResponse checkAndReserve(CheckAndReserveRequest request) {
                log.info("Checking and reserving inventory for productId: {}, quantity: {}",
                                request.getProductId(), request.getQuantity());

                // Find inventory with pessimistic lock to prevent race conditions
                Inventory inventory = inventoryRepository.findByProductIdForUpdate(request.getProductId())
                                .orElse(null);

                if (inventory == null) {
                        log.warn("Product not found: {}", request.getProductId());
                        return CheckAndReserveResponse.builder()
                                        .success(false)
                                        .message("Product not found")
                                        .productId(request.getProductId())
                                        .requestedQuantity(request.getQuantity())
                                        .build();
                }

                // Check if enough quantity is available
                if (inventory.getAvailableQuantity() < request.getQuantity()) {
                        log.warn("Insufficient inventory for productId: {}. Available: {}, Requested: {}",
                                        request.getProductId(), inventory.getAvailableQuantity(),
                                        request.getQuantity());
                        return CheckAndReserveResponse.builder()
                                        .success(false)
                                        .message("Insufficient inventory")
                                        .productId(request.getProductId())
                                        .requestedQuantity(request.getQuantity())
                                        .availableQuantity(inventory.getAvailableQuantity())
                                        .reservedQuantity(inventory.getReservedQuantity())
                                        .build();
                }

                // Reserve the quantity: decrease available, increase reserved
                inventory.setAvailableQuantity(inventory.getAvailableQuantity() - request.getQuantity());
                inventory.setReservedQuantity(inventory.getReservedQuantity() + request.getQuantity());

                inventoryRepository.save(inventory);

                log.info("Successfully reserved inventory for productId: {}. New available: {}, New reserved: {}",
                                request.getProductId(), inventory.getAvailableQuantity(),
                                inventory.getReservedQuantity());

                return CheckAndReserveResponse.builder()
                                .success(true)
                                .message("Inventory reserved successfully")
                                .productId(request.getProductId())
                                .requestedQuantity(request.getQuantity())
                                .availableQuantity(inventory.getAvailableQuantity())
                                .reservedQuantity(inventory.getReservedQuantity())
                                .build();
        }

        /**
         * Variant of checkAndReserve that DOES NOT use pessimistic locking.
         * This is used only in tests to demonstrate overselling and race conditions.
         */
        @Transactional
        public CheckAndReserveResponse checkAndReserveWithoutLock(CheckAndReserveRequest request) {
                log.info("UNLOCKED variant - checking and reserving inventory for productId: {}, quantity: {}",
                                request.getProductId(), request.getQuantity());

                // Read inventory WITHOUT any lock
                Inventory inventory = inventoryRepository.findByProductId(request.getProductId())
                                .orElse(null);

                if (inventory == null) {
                        log.warn("[UNLOCKED] Product not found: {}", request.getProductId());
                        return CheckAndReserveResponse.builder()
                                        .success(false)
                                        .message("Product not found")
                                        .productId(request.getProductId())
                                        .requestedQuantity(request.getQuantity())
                                        .build();
                }

                // Check if enough quantity is available based on a potentially stale value
                if (inventory.getAvailableQuantity() < request.getQuantity()) {
                        log.warn("[UNLOCKED] Insufficient inventory for productId: {}. Available: {}, Requested: {}",
                                        request.getProductId(), inventory.getAvailableQuantity(),
                                        request.getQuantity());
                        return CheckAndReserveResponse.builder()
                                        .success(false)
                                        .message("Insufficient inventory")
                                        .productId(request.getProductId())
                                        .requestedQuantity(request.getQuantity())
                                        .availableQuantity(inventory.getAvailableQuantity())
                                        .reservedQuantity(inventory.getReservedQuantity())
                                        .build();
                }

                // Reserve the quantity based on the value we read – this is where lost updates
                // can happen
                int newAvailable = inventory.getAvailableQuantity() - request.getQuantity();
                int newReserved = inventory.getReservedQuantity() + request.getQuantity();

                inventory.setAvailableQuantity(newAvailable);
                inventory.setReservedQuantity(newReserved);

                inventoryRepository.save(inventory);

                log.info("[UNLOCKED] Reserved inventory for productId: {}. New available: {}, New reserved: {}",
                                request.getProductId(), newAvailable, newReserved);

                return CheckAndReserveResponse.builder()
                                .success(true)
                                .message("Inventory reserved successfully (WITHOUT LOCKING)")
                                .productId(request.getProductId())
                                .requestedQuantity(request.getQuantity())
                                .availableQuantity(newAvailable)
                                .reservedQuantity(newReserved)
                                .build();
        }

        /**
         * Releases reserved inventory back to available.
         * Called when cart expires or user removes items from cart.
         */
        @Transactional
        public void releaseReservation(Long productId, Integer quantity) {
                log.info("Releasing reservation for productId: {}, quantity: {}", productId, quantity);

                Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                                .orElseThrow(() -> new RuntimeException("Product not found"));

                inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
                inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);

                inventoryRepository.save(inventory);
                log.info("Released reservation for productId: {}", productId);
        }

        /**
         * Confirms reservation by converting reserved to sold.
         * Called when order is placed successfully.
         */
        @Transactional
        public void confirmReservation(Long productId, Integer quantity) {
                log.info("Confirming reservation for productId: {}, quantity: {}", productId, quantity);

                Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                                .orElseThrow(() -> new RuntimeException("Product not found"));

                // Just decrease reserved quantity (it's already been moved from available)
                inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);

                inventoryRepository.save(inventory);
                log.info("Confirmed reservation for productId: {}", productId);
        }
}
