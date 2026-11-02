package com.example.shortener.exception;

import com.example.shortener.service.UrlValidator;

/** Domain exception (no HTTP reference) for a URL that fails {@link UrlValidator}'s checks. */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
