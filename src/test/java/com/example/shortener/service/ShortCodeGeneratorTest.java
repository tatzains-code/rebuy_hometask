package com.example.shortener.service;

import com.example.shortener.config.CodeProperties;
import com.example.shortener.exception.KeyspaceExhaustedException;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortCodeGeneratorTest {

    private static final long MULTIPLIER = 9_132_277L;
    private static final long OFFSET = 4_861_105L;

    private final ShortCodeGenerator generator =
            new ShortCodeGenerator(new CodeProperties(MULTIPLIER, OFFSET, Set.of()));

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 61L, 62L, ShortCodeGenerator.KEYSPACE - 1})
    void producesFourAlphabetCharactersForBoundaryIds(long id) {
        assertIsValidCode(generator.toCode(id));
    }

    @RepeatedTest(200)
    void producesFourAlphabetCharactersForRandomIds() {
        long id = ThreadLocalRandom.current().nextLong(ShortCodeGenerator.KEYSPACE);
        assertIsValidCode(generator.toCode(id));
    }

    @Test
    void distinctRandomIdsProduceDistinctCodes() {
        Set<Long> ids = new HashSet<>();
        while (ids.size() < 1_000_000) {
            ids.add(ThreadLocalRandom.current().nextLong(ShortCodeGenerator.KEYSPACE));
        }

        Set<String> codes = new HashSet<>();
        for (long id : ids) {
            codes.add(generator.toCode(id));
        }

        assertThat(codes).hasSize(1_000_000);
    }

    @Test
    @Tag("slow")
    void fullKeyspaceIsABijection() {
        Set<String> codes = new HashSet<>(20_000_000);
        for (long id = 0; id < ShortCodeGenerator.KEYSPACE; id++) {
            codes.add(generator.toCode(id));
        }

        assertThat(codes).hasSize((int) ShortCodeGenerator.KEYSPACE);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, ShortCodeGenerator.KEYSPACE})
    void throwsKeyspaceExhaustedOutsideValidRange(long id) {
        assertThatThrownBy(() -> generator.toCode(id))
                .isInstanceOf(KeyspaceExhaustedException.class);
    }

    private static void assertIsValidCode(String code) {
        assertThat(code).hasSize(ShortCodeGenerator.LENGTH);
        for (char c : code.toCharArray()) {
            assertThat(ShortCodeGenerator.ALPHABET.indexOf(c)).isGreaterThanOrEqualTo(0);
        }
    }
}
