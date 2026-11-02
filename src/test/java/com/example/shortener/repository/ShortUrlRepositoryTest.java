package com.example.shortener.repository;

import com.example.shortener.TestcontainersConfiguration;
import com.example.shortener.entity.ShortUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TestcontainersConfiguration.class)
class ShortUrlRepositoryTest {

    @Autowired
    private ShortUrlRepository repository;

    @Test
    void insertIfAbsentInsertsOnceThenNoOpsOnDuplicateUrlHash() {
        byte[] hash = sha256("https://example.com/a");

        int first = repository.insertIfAbsent(repository.nextId(), "AAAA", "https://example.com/a", hash, Instant.now());
        int second = repository.insertIfAbsent(repository.nextId(), "BBBB", "https://example.com/a", hash, Instant.now());

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);
    }

    @Test
    void findByCodeAndFindByUrlHashRoundTrip() {
        byte[] hash = sha256("https://example.com/b");
        repository.insertIfAbsent(repository.nextId(), "CCCC", "https://example.com/b", hash, Instant.now());

        assertThat(repository.findByCode("CCCC"))
                .get().extracting(ShortUrl::getTargetUrl).isEqualTo("https://example.com/b");
        assertThat(repository.findByUrlHash(hash))
                .get().extracting(ShortUrl::getCode).isEqualTo("CCCC");
    }

    @Test
    void nextIdIncrementsOnEachCall() {
        long first = repository.nextId();
        long second = repository.nextId();

        assertThat(second).isEqualTo(first + 1);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
