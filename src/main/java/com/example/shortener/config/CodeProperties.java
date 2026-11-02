package com.example.shortener.config;

import com.example.shortener.service.ShortCodeGenerator;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

/**
 * Code-generation settings under {@code app.code}: the affine permutation
 * {@code v = (multiplier * id + offset) mod KEYSPACE}. Neither
 * value may change once data exists.
 */
@Validated
@ConfigurationProperties(prefix = "app.code")
public record CodeProperties(
        @Min(1) @Max(ShortCodeGenerator.KEYSPACE - 1) long multiplier,
        @Min(0) @Max(ShortCodeGenerator.KEYSPACE - 1) long offset,
        Set<String> reserved) {

    @AssertTrue(message = "app.code.multiplier must be coprime with the keyspace (62^4): "
            + "it must be odd and not divisible by 31")
    boolean isMultiplierCoprimeWithKeyspace() {
        return gcd(multiplier, ShortCodeGenerator.KEYSPACE) == 1;
    }

    private static long gcd(long a, long b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
