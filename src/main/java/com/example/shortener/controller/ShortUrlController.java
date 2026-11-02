package com.example.shortener.controller;

import com.example.shortener.config.AppProperties;
import com.example.shortener.dto.CreateShortUrlRequest;
import com.example.shortener.dto.ShortUrlResponse;
import com.example.shortener.entity.ShortUrl;
import com.example.shortener.service.CreateResult;
import com.example.shortener.service.ShortUrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/short-urls")
@RequiredArgsConstructor
public class ShortUrlController {

    private final ShortUrlService service;
    private final AppProperties appProperties;

    @PostMapping
    public ResponseEntity<ShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request) {
        CreateResult result = service.create(request.url());
        ShortUrlResponse body = toResponse(result.shortUrl());

        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/short-urls/" + result.shortUrl().getCode())).body(body);
    }

    @GetMapping("/{code:[0-9A-Za-z]{4}}")
    public ResponseEntity<ShortUrlResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(toResponse(service.getByCode(code)));
    }

    private ShortUrlResponse toResponse(ShortUrl shortUrl) {
        String shortUrlString = appProperties.baseUrl() + "/" + shortUrl.getCode();
        return new ShortUrlResponse(shortUrl.getCode(), shortUrlString, shortUrl.getTargetUrl(), shortUrl.getCreatedAt());
    }
}
