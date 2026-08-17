package com.vv456.cart_service.repository;

import com.vv456.cart_service.model.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends MongoRepository<Cart, String> {

    Optional<Cart> findByUserId(String userId);

    void deleteByUserId(String userId);

    List<Cart> findByExpiresAtBefore(LocalDateTime dateTime);

    boolean existsByUserId(String userId);
}

