package com.example.shortener.controller;

import com.example.shortener.exception.ShortUrlNotFoundException;
import com.example.shortener.metrics.ShortenerMetrics;
import com.example.shortener.service.TargetUrlLookup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Redirect", description = "Follows a short code to its original URL.")
@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final TargetUrlLookup targetUrlLookup;
    private final ShortenerMetrics metrics;

    @Operation(summary = "Redirect to the original URL",
            description = "\"Try it out\" won't work here in the browser (the redirect is "
                    + "cross-origin, so CORS blocks it) — use `curl -i` instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirects to the original URL"),
            @ApiResponse(responseCode = "404", description = "No short URL exists for this code",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{code:[0-9A-Za-z]{4}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String targetUrl = targetUrlLookup.findTargetUrl(code);
        metrics.recordRedirect(targetUrl != null);
        if (targetUrl == null) {
            throw new ShortUrlNotFoundException(code);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(targetUrl))
                .cacheControl(CacheControl.noCache())
                .build();
    }
}
