package com.example.shortener.service;

import com.example.shortener.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * A separate bean (not a method on {@link ShortUrlService}) so {@code @Cacheable}
 * works through Spring's proxy — self-invocation would bypass it.
 */
@Component
@RequiredArgsConstructor
public class TargetUrlLookup {

    public static final String CACHE_NAME = "targetUrls";

    private final ShortUrlRepository repository;

    @Cacheable(cacheNames = CACHE_NAME, key = "#code", unless = "#result == null")
    public String findTargetUrl(String code) {
        return repository.findTargetUrlByCode(code).orElse(null);
    }
}
