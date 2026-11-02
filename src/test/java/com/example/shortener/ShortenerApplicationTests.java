package com.example.shortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Confirms Flyway migrates and Hibernate's {@code ddl-auto: validate} agrees with the schema. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShortenerApplicationTests {

    @Test
    void contextLoads() {
    }
}
