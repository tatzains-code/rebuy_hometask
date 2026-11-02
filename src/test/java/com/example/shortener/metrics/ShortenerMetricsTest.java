package com.example.shortener.metrics;

import com.example.shortener.TestcontainersConfiguration;
import com.example.shortener.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShortenerMetricsTest {

    @Autowired
    private MeterRegistry registry;
    @Autowired
    private ShortenerMetrics metrics;
    @Autowired
    private KeyspaceUsageGauge keyspaceUsageGauge;

    @Test
    void customMetersAreRegistered() {
        metrics.recordUrlCreated(true);
        metrics.recordRedirect(true);
        metrics.recordError(ErrorCode.NOT_FOUND);

        assertThat(registry.find("shortener.urls.created").counter()).isNotNull();
        assertThat(registry.find("shortener.redirects").counter()).isNotNull();
        assertThat(registry.find("shortener.errors").counter()).isNotNull();
        assertThat(registry.find("shortener.keyspace.used.ratio").gauge()).isNotNull();
    }
}
