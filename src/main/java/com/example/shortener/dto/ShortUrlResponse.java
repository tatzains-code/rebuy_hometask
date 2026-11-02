package com.example.shortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ShortUrlResponse(
        @Schema(description = "The 4-character short code", example = "KOav") String code,
        @Schema(description = "The full short URL", example = "http://localhost:8080/KOav") String shortUrl,
        @Schema(description = "The original URL this code redirects to", example = "https://www.google.com/search?q=spring+boot") String targetUrl,
        @Schema(description = "When this short URL was created") Instant createdAt) {
}
