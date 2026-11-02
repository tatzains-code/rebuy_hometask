package com.example.shortener.service;

import com.example.shortener.config.CodeProperties;
import com.example.shortener.exception.KeyspaceExhaustedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns a sequence id into a 4-character Base62 code via an affine
 * permutation. Pure function of {@code id} and {@link CodeProperties} — no
 * database access, randomness, or hashing.
 */
@Component
@RequiredArgsConstructor
public class ShortCodeGenerator {

    public static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final int LENGTH = 4;
    public static final long KEYSPACE = 14_776_336L; // 62^4

    private static final int BASE = 62;

    private final CodeProperties properties;

    public String toCode(long id) {
        if (id < 0 || id >= KEYSPACE) {
            throw new KeyspaceExhaustedException(id);
        }

        long value = Math.floorMod(properties.multiplier() * id + properties.offset(), KEYSPACE);

        char[] chars = new char[LENGTH];
        for (int i = LENGTH - 1; i >= 0; i--) {
            chars[i] = ALPHABET.charAt((int) (value % BASE));
            value /= BASE;
        }
        return new String(chars);
    }
}
