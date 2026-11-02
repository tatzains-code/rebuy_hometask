package com.example.shortener.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings under the {@code app.} prefix.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotBlank String baseUrl) {
}
