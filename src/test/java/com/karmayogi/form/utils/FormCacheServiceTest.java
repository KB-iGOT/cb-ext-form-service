package com.karmayogi.form.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author anil
 */
@ExtendWith(MockitoExtension.class)
class FormCacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private FormCacheService formCacheService;

    @BeforeEach
    void setUp() {
        formCacheService = new FormCacheService(redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(formCacheService, "bulkStatusTtlSeconds", 1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void submissionStatusIsCachedWithTheBulkStatusTtlInSeconds() {
        formCacheService.putSubmissionStatus("user-1", "form-1", "ctx-1", true);

        verify(valueOperations).set("bulk-status:user-1:form-1:ctx-1", "true", 1L, TimeUnit.SECONDS);
    }
}
