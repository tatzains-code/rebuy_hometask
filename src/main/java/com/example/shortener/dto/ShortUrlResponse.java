package com.example.shortener.dto;

import java.time.Instant;

public record ShortUrlResponse(String code, String shortUrl, String targetUrl, Instant createdAt) {
}
