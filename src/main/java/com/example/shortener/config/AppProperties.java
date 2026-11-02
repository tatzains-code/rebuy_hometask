package com.example.shortener.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings under the {@code app.} prefix.
 *
 * @param baseUrl origin short URLs are built from; never taken from the request Host header
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotBlank String baseUrl) {
}
