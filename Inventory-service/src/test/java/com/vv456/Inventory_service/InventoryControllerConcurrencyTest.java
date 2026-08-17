package com.vv456.Inventory_service;

import com.vv456.Inventory_service.dto.CheckAndReserveRequest;
import com.vv456.Inventory_service.dto.CheckAndReserveResponse;
import com.vv456.Inventory_service.entities.Inventory;
import com.vv456.Inventory_service.entities.Product;
import com.vv456.Inventory_service.repository.InventoryRepository;
import com.vv456.Inventory_service.repository.ProductRepository;
import com.vv456.Inventory_service.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ==================================================================================
 * REAL-WORLD INVENTORY CONTROLLER STRESS TESTS
 * ==================================================================================
 * 
 * This test demonstrates:
 * 1. HOW THE SYSTEM BREAKS without proper locking
 * 2. HOW IT'S PROTECTED with pessimistic locking
 * 3. RACE CONDITIONS in action
 * 4. OVERSELLING scenarios
 * 5. Different isolation levels and their effects
 * 
 * SCENARIO: Limited stock items (like flash sales, concert tickets)
 * 
 * Run individual tests to see real-world concurrency problems!
 */
@SpringBootTest
@ActiveProfiles("local")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryControllerConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static Long PRODUCT_ID; // Will be set dynamically

    @BeforeEach
    @Transactional
    void setup() {
        // Create or find test product
        Product existingProduct = productRepository.findByName("Limited Edition Product").orElse(null);
        
        if (existingProduct != null) {
            PRODUCT_ID = existingProduct.getId();
            log.info("Using existing test product with ID: {}", PRODUCT_ID);
        } else {
            Product product = Product.builder()
                    .name("Limited Edition Product")
                    .price(BigDecimal.valueOf(99.99))
                    .build();
            Product saved = productRepository.save(product);
            PRODUCT_ID = saved.getId(); // Get the auto-generated ID
            log.info("Created new test product with ID: {}", PRODUCT_ID);
        }

        log.info("\n\n╔════════════════════════════════════════════════════════════╗");
        log.info("║  Setting up test with fresh inventory                     ║");
        log.info("╚════════════════════════════════════════════════════════════╝\n");
    }

    // ==================================================================================
    // TEST 1: YOUR SPECIFIC SCENARIO - Overselling 7 Items
    // ==================================================================================

    @Test
    @Order(1)
    @DisplayName("1. 🎯 WITH LOCKING: 7 Available, 3 Requests × 3 Items = NO oversell")
    void testOverselling_SevenAvailable_ThreeRequestsOfThree() throws InterruptedException {
        log.info("\n╔════════════════════════════════════════════════════════════╗");
        log.info("║  CRITICAL SCENARIO: Can we oversell?                      ║");
        log.info("║  Available: 7 items                                        ║");
        log.info("║  Request 1: 3 items                                        ║");
        log.info("║  Request 2: 3 items                                        ║");
        log.info("║  Request 3: 3 items                                        ║");
        log.info("║  Total Requested: 9 items (MORE than available!)          ║");
        log.info("╚════════════════════════════════════════════════════════════╝\n");

        // Setup: Available=7, Reserved=3 (total stock=10)
        setupInventory(PRODUCT_ID, 7, 3);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(3);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Launch 3 concurrent requests, each trying to reserve 3 items (WITH locking)
        for (int i = 1; i <= 3; i++) {
            int requestNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    log.info("🛒 Customer {}: Attempting to buy 3 items...", requestNum);
                    
                    CheckAndReserveRequest request = new CheckAndReserveRequest();
                    request.setProductId(PRODUCT_ID);
                    request.setQuantity(3);
                    
                    CheckAndReserveResponse response = inventoryService.checkAndReserve(request);
                    
                    if (response.isSuccess()) {
                        successCount.incrementAndGet();
                        log.info("✅ Customer {}: SUCCESS! Reserved 3 items", requestNum);
                    } else {
                        failureCount.incrementAndGet();
                        log.info("❌ Customer {}: FAILED - {}", requestNum, response.getMessage());
                    }
                    
                } catch (Exception e) {
                    log.error("Customer {} error: {}", requestNum, e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads simultaneously
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Check final state
        Inventory finalInventory = inventoryRepository.findById(PRODUCT_ID).orElseThrow();
        
        log.info("\n📊 RESULTS:");
        log.info("   Initial available: 7");
        log.info("   Successful requests: {}", successCount.get());
        log.info("   Failed requests: {}", failureCount.get());
        log.info("   Final available: {}", finalInventory.getAvailableQuantity());
        log.info("   Final reserved: {}", finalInventory.getReservedQuantity());
        log.info("   Total reserved by requests: {}", successCount.get() * 3);

        // Verification – WITH pessimistic locking we expect consistent, safe results
        int totalReservedByRequests = successCount.get() * 3;
        int expectedReserved = 3 + totalReservedByRequests; // Initial 3 + new reservations
        
        log.info("\n🔍 VERIFICATION:");
        log.info("   Expected: Only 2 requests should succeed (2×3=6), 1 should fail");
        log.info("   Actual: {} succeeded, {} failed", successCount.get(), failureCount.get());
        
        assertEquals(2, successCount.get(), "Only 2 requests should succeed (7 available ÷ 3 per request)");
        assertEquals(1, failureCount.get(), "1 request should fail");
        assertEquals(1, finalInventory.getAvailableQuantity(), "Should have 1 item left (7-6)");
        assertEquals(expectedReserved, finalInventory.getReservedQuantity(), "Should have 9 reserved (3+6)");
        
        log.info("\n✅ RESULT: Pessimistic locking PREVENTED overselling!");
        log.info("   The system correctly rejected the 3rd request!\n");
    }

    // ==================================================================================
    // TEST 1B: SAME SCENARIO WITHOUT LOCKING – Demonstrates overselling
    // ==================================================================================

    @Test
    @Order(2)
    @DisplayName("1B. ❌ WITHOUT LOCKING: 7 Available, 3 Requests × 3 Items = Oversell / Inconsistent")
    void testOverselling_SevenAvailable_ThreeRequestsOfThree_NoLock() throws InterruptedException {
        log.info("\n╔════════════════════════════════════════════════════════════╗");
        log.info("║  CRITICAL SCENARIO WITHOUT LOCKING: Can we oversell?      ║");
        log.info("║  Available: 7 items                                        ║");
        log.info("║  Request 1: 3 items                                        ║");
        log.info("║  Request 2: 3 items                                        ║");
        log.info("║  Request 3: 3 items                                        ║");
        log.info("║  Total Requested: 9 items (MORE than available!)          ║");
        log.info("╚════════════════════════════════════════════════════════════╝\n");

        // Setup: Available=7, Reserved=3 (total stock=10)
        setupInventory(PRODUCT_ID, 7, 3);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(3);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Launch 3 concurrent requests, each trying to reserve 3 items – WITHOUT locking
        for (int i = 1; i <= 3; i++) {
            int requestNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    log.info("🛒 [NO LOCK] Customer {}: Attempting to buy 3 items...", requestNum);

                    CheckAndReserveRequest request = new CheckAndReserveRequest();
                    request.setProductId(PRODUCT_ID);
                    request.setQuantity(3);

                    CheckAndReserveResponse response = inventoryService.checkAndReserveWithoutLock(request);

                    if (response.isSuccess()) {
                        successCount.incrementAndGet();
                        log.info("✅ [NO LOCK] Customer {}: SUCCESS! Reserved 3 items", requestNum);
                    } else {
                        failureCount.incrementAndGet();
                        log.info("❌ [NO LOCK] Customer {}: FAILED - {}", requestNum, response.getMessage());
                    }

                } catch (Exception e) {
                    log.error("[NO LOCK] Customer {} error: {}", requestNum, e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads simultaneously
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Check final state
        Inventory finalInventory = inventoryRepository.findById(PRODUCT_ID).orElseThrow();

        log.info("\n📊 [NO LOCK] RESULTS:");
        log.info("   Initial available: 7");
        log.info("   Successful requests: {}", successCount.get());
        log.info("   Failed requests: {}", failureCount.get());
        log.info("   Final available: {}", finalInventory.getAvailableQuantity());
        log.info("   Final reserved: {}", finalInventory.getReservedQuantity());
        log.info("   Total reserved by requests: {}", successCount.get() * 3);

        int totalReservedByRequests = successCount.get() * 3;
        int expectedReservedIfSafe = 3 + Math.min(2, successCount.get()) * 3; // what we'd expect with proper locking

        log.info("\n🔍 [NO LOCK] VERIFICATION:");
        log.info("   Without locking, we EXPECT inconsistent results / oversell");
        log.info("   Successful: {}, Failed: {}", successCount.get(), failureCount.get());
        log.info("   Final available: {}", finalInventory.getAvailableQuantity());
        log.info("   Final reserved: {} (initial 3 + {} from successful requests)",
                finalInventory.getReservedQuantity(), totalReservedByRequests);

        // This assertion is intentionally the SAME as the locked version –
        // we EXPECT it to fail sometimes, revealing overselling / inconsistency.
        assertEquals(2, successCount.get(), "[NO LOCK] Only 2 requests SHOULD succeed if there was proper locking");
        assertEquals(1, failureCount.get(), "[NO LOCK] 1 request SHOULD fail if there was proper locking");
        assertEquals(1, finalInventory.getAvailableQuantity(), "[NO LOCK] Should have 1 item left (7-6) with proper locking");
        assertEquals(expectedReservedIfSafe, finalInventory.getReservedQuantity(),
                "[NO LOCK] Reserved should match a safe scenario (no oversell)");

        log.info("\n❌ [NO LOCK] RESULT: If this test ever passes, it's by luck.");
        log.info("   Intermittent failures / inconsistent final state demonstrate why we need pessimistic locking.\n");
    }

    // ==================================================================================
    // TEST 2: 100 Concurrent Requests - Stress Test
    // ==================================================================================

    @Test
    @Order(2)
    @DisplayName("2. 💥 STRESS TEST: 100 Customers × 1 Item, Only 50 Available")
    void testConcurrentReservations_100Customers_50Stock() throws InterruptedException {
        log.info("\n╔════════════════════════════════════════════════════════════╗");
        log.info("║  BLACK FRIDAY SCENARIO: Flash Sale!                       ║");
        log.info("║  Stock: 50 items                                           ║");
        log.info("║  Customers: 100 people clicking 'Buy' simultaneously       ║");
        log.info("╚════════════════════════════════════════════════════════════╝\n");

        setupInventory(PRODUCT_ID, 50, 0);

        int numCustomers = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numCustomers);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= numCustomers; i++) {
            int customerNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    CheckAndReserveRequest request = new CheckAndReserveRequest();
                    request.setProductId(PRODUCT_ID);
                    request.setQuantity(1);
                    
                    CheckAndReserveResponse response = inventoryService.checkAndReserve(request);
                    
                    if (response.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    log.error("Customer {} error: {}", customerNum, e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        log.info("🚀 Starting 100 concurrent purchase attempts...");
        startLatch.countDown();
        endLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;
        Inventory finalInventory = inventoryRepository.findById(PRODUCT_ID).orElseThrow();

        log.info("\n📊 BLACK FRIDAY RESULTS:");
        log.info("   Initial stock: 50");
        log.info("   Total customers: 100");
        log.info("   Successful purchases: {}", successCount.get());
        log.info("   Rejected customers: {}", 100 - successCount.get());
        log.info("   Final available: {}", finalInventory.getAvailableQuantity());
        log.info("   Final reserved: {}", finalInventory.getReservedQuantity());
        log.info("   Duration: {}ms", duration);

        assertEquals(50, successCount.get(), "Exactly 50 should succeed");
        assertEquals(0, finalInventory.getAvailableQuantity(), "All stock should be reserved");
        assertEquals(50, finalInventory.getReservedQuantity(), "50 items should be reserved");
        
        log.info("\n✅ SUCCESS: No overselling! Pessimistic locking works!");
        log.info("   System handled 100 concurrent requests correctly!\n");
    }

    // ==================================================================================
    // TEST 3: Demonstrating Lost Updates WITHOUT Locking
    // ==================================================================================

    @Test
    @Order(3)
    @DisplayName("3. ⚠️  DANGEROUS: Lost Updates with Read-Without-Lock")
    void testLostUpdates_WithoutLocking() throws InterruptedException {
        log.info("\n╔════════════════════════════════════════════════════════════╗");
        log.info("║  DEMONSTRATING: What happens WITHOUT proper locking       ║");
        log.info("║  This simulates reading without FOR UPDATE                ║");
        log.info("╚════════════════════════════════════════════════════════════╝\n");

        setupInventory(PRODUCT_ID, 100, 0);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(3);

        // Simulate concurrent operations with delays (like reading without lock)
        for (int i = 1; i <= 3; i++) {
            int customerNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    // Simulate: Read without lock, process, then update
                    simulateReadProcessUpdate(PRODUCT_ID, 10, customerNum);
                    
                } catch (Exception e) {
                    log.error("Customer {} error: {}", customerNum, e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        log.info("Starting 3 concurrent operations...");
        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Inventory finalInventory = inventoryRepository.findById(PRODUCT_ID).orElseThrow();

        log.info("\n⚠️  DANGER ZONE RESULTS:");
        log.info("   Initial: 100");
        log.info("   Expected after 3×10: 70");
        log.info("   Actual: {} ⚠️", finalInventory.getAvailableQuantity());
        
        if (finalInventory.getAvailableQuantity() != 70) {
            log.info("\n❌ LOST UPDATE DETECTED!");
            log.info("   Some updates were overwritten!");
            log.info("   This is WHY we need locking!");
        }
        
        log.info("\n📚 LESSON: Without proper locking, concurrent updates can be lost!\n");
    }

    // ==================================================================================
    // TEST 4: Race Condition - Multiple Items Reserved Simultaneously
    // ==================================================================================

    @Test
    @Order(4)
    @DisplayName("4. 🏁 RACE CONDITION: 5 Customers, Last Item Wins")
    void testRaceCondition_LastItemWins() throws InterruptedException {
        log.info("\n╔════════════════════════════════════════════════════════════╗");
        log.info("║  RACE CONDITION: Only 1 item left!                        ║");
        log.info("║  5 customers hit 'Buy' at the exact same microsecond      ║");
        log.info("║  Who gets it?                                              ║");
        log.info("╚════════════════════════════════════════════════════════════╝\n");

        setupInventory(PRODUCT_ID, 1, 0); // Only 1 item available!

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(5);
        
        List<String> winners = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 1; i <= 5; i++) {
            int customerNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    CheckAndReserveRequest request = new CheckAndReserveRequest();
                    request.setProductId(PRODUCT_ID);
                    request.setQuantity(1);
                    
                    CheckAndReserveResponse response = inventoryService.checkAndReserve(request);
                    
                    if (response.isSuccess()) {
                        successCount.incrementAndGet();
                        winners.add("Customer " + customerNum);
                        log.info("🎉 Customer {}: WON THE RACE!", customerNum);
                    } else {
                        log.info("😞 Customer {}: Too slow, item sold out", customerNum);
                    }
                    
                } catch (Exception e) {
                    log.error("Customer {} error: {}", customerNum, e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Inventory finalInventory = inventoryRepository.findById(PRODUCT_ID).orElseThrow();

        log.info("\n🏆 RACE RESULTS:");
        log.info("   Winners: {}", winners);
        log.info("   Success count: {}", successCount.get());
        log.info("   Final available: {}", finalInventory.getAvailableQuantity());

        assertEquals(1, successCount.get(), "Only 1 customer should win");
        assertEquals(0, finalInventory.getAvailableQuantity(), "Item should be sold");
        
        log.info("\n✅ FAIR RACE: Only one winner, no double-selling!\n");
    }

    // ==================================================================================
    // TEST 5: Isolation Level Impact - Read Committed vs Repeatable Read
    // ==================================================================================

    @Test
    @Order(5)
    @DisplayName("5. 🔒 ISOLATION: Non-repeatable Read Demonstration")
    void testIsolationLevel_NonRepeatableRead() throws InterruptedException {
        log.info("\n╔════════════════════════════════════════════════════════════╗");
        log.info("║  ISOLATION LEVELS: What can transactions see?             ║");
        log.info("╚════════════════════════════════════════════════════════════╝\n");

        setupInventory(PRODUCT_ID, 100, 0);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        List<Integer> readValues = new CopyOnWriteArrayList<>();

        // Thread 1: Long-running transaction, reads twice
        executor.submit(() -> {
            try {
                startLatch.await();
                readMultipleTimes(PRODUCT_ID, readValues);
            } catch (Exception e) {
                log.error("Reader error: {}", e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: Updates in the middle
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(100); // Let reader start first
                
                CheckAndReserveRequest request = new CheckAndReserveRequest();
                request.setProductId(PRODUCT_ID);
                request.setQuantity(20);
                
                inventoryService.checkAndReserve(request);
                log.info("Updater: Reserved 20 items during reader's transaction");
                
            } catch (Exception e) {
                log.error("Updater error: {}", e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("\n📚 ISOLATION LESSON:");
        log.info("   With READ_COMMITTED: Reader might see different values");
        log.info("   With REPEATABLE_READ: Reader sees consistent snapshot");
        log.info("   Values seen: {}", readValues);
        log.info("\n");
    }

    // ==================================================================================
    // HELPER METHODS
    // ==================================================================================

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private void setupInventory(Long productId, int available, int reserved) {
        log.info("📦 Setting up inventory for Product ID: {}", productId);

        // For test setup, we can use direct SQL to avoid JPA lifecycle edge cases
        // with @MapsId and detached entities. This keeps the concurrency behavior
        // under test while making seeding deterministic.

        // Ensure product exists
        productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        // Clear any existing inventory row for this product
        jdbcTemplate.update("DELETE FROM inventory WHERE product_id = ?", productId);

        // Insert fresh inventory row
        jdbcTemplate.update(
                "INSERT INTO inventory (product_id, available_quantity, reserved_quantity) VALUES (?, ?, ?)",
                productId, available, reserved
        );

        log.info("📦 Inventory setup complete via SQL: Available={}, Reserved={}", available, reserved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    void simulateReadProcessUpdate(Long productId, int quantity, int customerNum) {
        try {
            // Read without lock (dangerous!)
            Inventory inventory = inventoryRepository.findById(productId).orElseThrow();
            int currentQty = inventory.getAvailableQuantity();
            
            log.info("Customer {}: Read quantity = {}", customerNum, currentQty);
            
            // Simulate processing time
            Thread.sleep(50);
            
            // Update based on old value (race condition!)
            inventory.setAvailableQuantity(currentQty - quantity);
            inventoryRepository.save(inventory);
            
            log.info("Customer {}: Updated to {}", customerNum, currentQty - quantity);
            
        } catch (Exception e) {
            log.error("Error in simulate: {}", e.getMessage());
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    void readMultipleTimes(Long productId, List<Integer> readValues) {
        try {
            Inventory inv1 = inventoryRepository.findById(productId).orElseThrow();
            int firstRead = inv1.getAvailableQuantity();
            readValues.add(firstRead);
            log.info("Reader: First read = {}", firstRead);
            
            Thread.sleep(200); // Wait for updater
            
            Inventory inv2 = inventoryRepository.findById(productId).orElseThrow();
            int secondRead = inv2.getAvailableQuantity();
            readValues.add(secondRead);
            log.info("Reader: Second read = {}", secondRead);
            
            if (firstRead != secondRead) {
                log.info("⚠️  NON-REPEATABLE READ detected! {} → {}", firstRead, secondRead);
            }
            
        } catch (Exception e) {
            log.error("Reader error: {}", e.getMessage());
        }
    }
}
