package com.example.shortener.metrics;

import com.example.shortener.repository.ShortUrlRepository;
import com.example.shortener.service.ShortCodeGenerator;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Exposes {@code shortener.keyspace.used.ratio}, refreshed every 60s by
 * {@code @Scheduled} (a cheap DB read) rather than recomputed on every scrape.
 */
@Component
@RequiredArgsConstructor
public class KeyspaceUsageGauge {

    private final ShortUrlRepository repository;
    private final MeterRegistry registry;
    private final AtomicReference<Double> usedRatio = new AtomicReference<>(0.0);

    @PostConstruct
    void registerAndRefresh() {
        Gauge.builder("shortener.keyspace.used.ratio", usedRatio, AtomicReference::get).register(registry);
        refresh();
    }

    @Scheduled(fixedRate = 60_000)
    void refresh() {
        long lastValue = repository.lastSequenceValue();
        usedRatio.set(lastValue / (double) ShortCodeGenerator.KEYSPACE);
    }
}
