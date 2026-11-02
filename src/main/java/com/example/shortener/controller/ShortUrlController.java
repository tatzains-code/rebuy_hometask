package com.example.shortener.controller;

import com.example.shortener.config.AppProperties;
import com.example.shortener.dto.CreateShortUrlRequest;
import com.example.shortener.dto.ShortUrlResponse;
import com.example.shortener.entity.ShortUrl;
import com.example.shortener.metrics.ShortenerMetrics;
import com.example.shortener.service.CreateResult;
import com.example.shortener.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Short URLs", description = "Create and look up short URLs.")
@RestController
@RequestMapping("/api/v1/short-urls")
@RequiredArgsConstructor
public class ShortUrlController {

    private final ShortUrlService service;
    private final AppProperties appProperties;
    private final ShortenerMetrics metrics;

    @Operation(summary = "Shorten a URL",
            description = "Returns the existing short URL if this URL was already shortened before.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "A new short URL was created"),
            @ApiResponse(responseCode = "200", description = "This URL was already shortened; returns the existing one"),
            @ApiResponse(responseCode = "400", description = "The request body or URL is invalid",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported content type",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Temporarily unable to create a short URL",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<ShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request) {
        CreateResult result = service.create(request.url());
        metrics.recordUrlCreated(result.created());
        ShortUrlResponse body = toResponse(result.shortUrl());

        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/short-urls/" + result.shortUrl().getCode())).body(body);
    }

    @Operation(summary = "Look up a short URL", description = "Returns the full details for a short code.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "No short URL exists for this code",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{code:[0-9A-Za-z]{4}}")
    public ResponseEntity<ShortUrlResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(toResponse(service.getByCode(code)));
    }

    private ShortUrlResponse toResponse(ShortUrl shortUrl) {
        String shortUrlString = appProperties.baseUrl() + "/" + shortUrl.getCode();
        return new ShortUrlResponse(shortUrl.getCode(), shortUrlString, shortUrl.getTargetUrl(), shortUrl.getCreatedAt());
    }
}
