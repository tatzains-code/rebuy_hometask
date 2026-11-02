package com.example.shortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateShortUrlRequest(
        @NotBlank @Size(max = 2048)
        @Schema(description = "The URL to shorten", example = "https://www.google.com/search?q=spring+boot")
        String url) {
}
