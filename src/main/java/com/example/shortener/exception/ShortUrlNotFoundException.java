package com.example.shortener.exception;

/** Domain exception (no HTTP reference) for an unknown short-URL code. */
public class ShortUrlNotFoundException extends RuntimeException {

    public ShortUrlNotFoundException(String code) {
        super("No short URL found for code: " + code);
    }
}
