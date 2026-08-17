package com.vv456.cart_service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vv456.cart_service.model.Cart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class CartCacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CART_CACHE_PREFIX = "cart:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    public void saveCart(Cart cart) {
        try {
            String key = generateKey(cart.getUserId());
            redisTemplate.opsForValue().set(key, cart, CACHE_TTL);
            log.info("Cart cached for user: {}", cart.getUserId());
        } catch (Exception e) {
            log.error("Failed to cache cart for user: {}", cart.getUserId(), e);
        }
    }

    public Optional<Cart> getCart(String userId) {
        try {
            String key = generateKey(userId);
            Object cartObj = redisTemplate.opsForValue().get(key);
            if (cartObj != null) {
                log.info("Cart cache hit for user: {}", userId);
                // Handle deserialization - GenericJackson2JsonRedisSerializer returns
                // LinkedHashMap
                if (cartObj instanceof Cart) {
                    return Optional.of((Cart) cartObj);
                } else if (cartObj instanceof LinkedHashMap) {
                    // Convert LinkedHashMap to Cart using ObjectMapper
                    Cart cart = objectMapper.convertValue(cartObj, Cart.class);
                    return Optional.of(cart);
                } else {
                    log.warn("Unexpected cart object type: {} for user: {}", cartObj.getClass(), userId);
                    return Optional.empty();
                }
            }
            log.info("Cart cache miss for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to get cart from cache for user: {}", userId, e);
        }
        return Optional.empty();
    }

    public void deleteCart(String userId) {
        try {
            String key = generateKey(userId);
            redisTemplate.delete(key);
            log.info("Cart cache deleted for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to delete cart cache for user: {}", userId, e);
        }
    }

    public void refreshTTL(String userId) {
        try {
            String key = generateKey(userId);
            redisTemplate.expire(key, CACHE_TTL);
            log.debug("Cart cache TTL refreshed for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to refresh cart cache TTL for user: {}", userId, e);
        }
    }

    private String generateKey(String userId) {
        return CART_CACHE_PREFIX + userId;
    }
}
