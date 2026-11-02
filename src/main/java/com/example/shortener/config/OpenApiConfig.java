package com.example.shortener.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "URL Shortener API",
                version = "1.0",
                description = "Turns a long URL into a short, shareable 4-character link."
        ),
        servers = @Server(url = "${app.base-url}")
)
public class OpenApiConfig {
}
