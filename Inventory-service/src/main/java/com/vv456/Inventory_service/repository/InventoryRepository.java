package com.vv456.Inventory_service.repository;

import com.vv456.Inventory_service.entities.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    // Same query but WITHOUT any locking used only for tests
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    Optional<Inventory> findByProductId(@Param("productId") Long productId);

    @Query("SELECT i FROM Inventory i WHERE i.productId IN :productIds")
    List<Inventory> findByProductIdIn(@Param("productIds") List<Long> productIds);
}
