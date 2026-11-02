package com.example.shortener.controller;

import com.example.shortener.exception.ShortUrlNotFoundException;
import com.example.shortener.service.TargetUrlLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final TargetUrlLookup targetUrlLookup;

    @GetMapping("/{code:[0-9A-Za-z]{4}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String targetUrl = targetUrlLookup.findTargetUrl(code);
        if (targetUrl == null) {
            throw new ShortUrlNotFoundException(code);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(targetUrl))
                .cacheControl(CacheControl.noCache())
                .build();
    }
}
