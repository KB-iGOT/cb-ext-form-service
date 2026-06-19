package com.karmayogi.form.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author anil
 */
@RequiredArgsConstructor
@Service
public class FormCacheService {

    private static final Logger log = LoggerFactory.getLogger(FormCacheService.class);

    private static final String  PREFIX = "form:";

    @Value("${form.cache.ttl.hours:1}")
    private long ttl;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public Map<String, Object> get(String formId) {
        try {
            String value = redisTemplate.opsForValue().get(PREFIX + formId);
            if (value == null) {
                log.debug("Cache MISS form:{}", formId);
                return Map.of();
            }
            log.debug("Cache HIT form:{}", formId);
            return objectMapper.readValue(value, Map.class);
        } catch (Exception e) {
            log.warn("Cache GET error formId={}: {}", formId, e.getMessage());
            return Map.of();
        }
    }

    public void put(String formId, Map<String, Object> data) {
        try {
            redisTemplate.opsForValue().set(
                    PREFIX + formId,
                    objectMapper.writeValueAsString(data),
                    ttl, TimeUnit.HOURS
            );
            log.debug("Cache PUT form:{} TTL={}h", formId, ttl);
        } catch (Exception e) {
            log.warn("Cache PUT error formId={}: {}", formId, e.getMessage());
        }
    }

    public void invalidate(String formId) {
        try {
            redisTemplate.delete(PREFIX + formId);
            log.info("Cache INVALIDATED form:{}", formId);
        } catch (Exception e) {
            log.warn("Cache INVALIDATE error formId={}: {}", formId, e.getMessage());
        }
    }

    public String getCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error while reading the cache", e);
            return null;
        }
    }

    public void putCache(String key,
                         String value,
                         long ttl,
                         TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, ttl, unit);
    }


}

