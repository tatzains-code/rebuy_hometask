package com.example.shortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves that the application context loads with Flyway migrations applied
 * and Hibernate's {@code ddl-auto: validate} agreeing with the resulting
 * schema.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShortenerApplicationTests {

    @Test
    void contextLoads() {
    }
}
