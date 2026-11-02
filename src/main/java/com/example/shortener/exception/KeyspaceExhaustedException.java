package com.example.shortener.exception;

import com.example.shortener.service.ShortCodeGenerator;

/**
 * Domain exception (no HTTP reference) for a sequence id outside
 * {@code [0, KEYSPACE)} — negative, or the keyspace is exhausted.
 */
public class KeyspaceExhaustedException extends RuntimeException {

    public KeyspaceExhaustedException(long id) {
        super("Sequence id " + id + " is outside the keyspace [0, " + ShortCodeGenerator.KEYSPACE + ")");
    }

    /** For the DB sequence itself reaching its {@code MAXVALUE} (SQLState {@code 2200H}). */
    public KeyspaceExhaustedException() {
        super("The code keyspace is exhausted: the sequence has reached its maximum value");
    }
}
