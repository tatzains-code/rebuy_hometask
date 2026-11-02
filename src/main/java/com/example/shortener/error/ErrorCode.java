package com.example.shortener.error;

public enum ErrorCode {
    VALIDATION_FAILED,
    INVALID_URL,
    MALFORMED_REQUEST,
    SHORT_URL_NOT_FOUND,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    KEYSPACE_EXHAUSTED,
    STORAGE_UNAVAILABLE,
    INTERNAL_ERROR;

    /** e.g. {@code VALIDATION_FAILED} → {@code validation-failed}, for the ProblemDetail {@code type} URI. */
    public String toKebabCase() {
        return name().toLowerCase().replace('_', '-');
    }
}
