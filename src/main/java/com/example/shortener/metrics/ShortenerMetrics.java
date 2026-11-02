package com.example.shortener.metrics;

import com.example.shortener.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Business counters. Never tagged with a short code or URL — only fixed, low-cardinality values. */
@Component
@RequiredArgsConstructor
public class ShortenerMetrics {

    private final MeterRegistry registry;

    public void recordUrlCreated(boolean created) {
        registry.counter("shortener.urls.created", "result", created ? "created" : "existing").increment();
    }

    public void recordRedirect(boolean found) {
        registry.counter("shortener.redirects", "result", found ? "found" : "not_found").increment();
    }

    public void recordError(ErrorCode errorCode) {
        registry.counter("shortener.errors", "code", errorCode.name()).increment();
    }
}
