package com.example.shortener.service;

import com.example.shortener.repository.ShortUrlRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises the real {@code @Cacheable} proxy against an isolated Caffeine cache (no DB). */
@SpringBootTest(classes = {TargetUrlLookupCacheTest.TestConfig.class, TargetUrlLookup.class})
class TargetUrlLookupCacheTest {

    @Autowired
    private TargetUrlLookup targetUrlLookup;

    @MockitoBean
    private ShortUrlRepository repository;

    @Test
    void secondLookupForTheSameCodeDoesNotHitTheRepository() {
        when(repository.findTargetUrlByCode("ABCD")).thenReturn(Optional.of("https://example.com/page"));

        String first = targetUrlLookup.findTargetUrl("ABCD");
        String second = targetUrlLookup.findTargetUrl("ABCD");

        assertThat(first).isEqualTo("https://example.com/page");
        assertThat(second).isEqualTo("https://example.com/page");
        verify(repository, times(1)).findTargetUrlByCode("ABCD");
    }

    @Test
    void unknownCodeThenCreatedIsNotServedAStaleNegativeResult() {
        when(repository.findTargetUrlByCode("ZZZZ")).thenReturn(Optional.empty());
        assertThat(targetUrlLookup.findTargetUrl("ZZZZ")).isNull();

        when(repository.findTargetUrlByCode("ZZZZ")).thenReturn(Optional.of("https://example.com/new"));
        assertThat(targetUrlLookup.findTargetUrl("ZZZZ")).isEqualTo("https://example.com/new");

        verify(repository, times(2)).findTargetUrlByCode("ZZZZ");
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            CaffeineCacheManager manager = new CaffeineCacheManager(TargetUrlLookup.CACHE_NAME);
            manager.setCaffeine(Caffeine.newBuilder());
            return manager;
        }
    }
}
