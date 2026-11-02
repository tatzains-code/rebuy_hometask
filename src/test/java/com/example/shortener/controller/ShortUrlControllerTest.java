package com.example.shortener.controller;

import com.example.shortener.config.AppProperties;
import com.example.shortener.entity.ShortUrl;
import com.example.shortener.exception.ShortUrlNotFoundException;
import com.example.shortener.service.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShortUrlController.class)
class ShortUrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService service;

    @MockitoBean
    private AppProperties appProperties;

    @Test
    void returnsShortUrlResponseWhenCodeIsFound() throws Exception {
        when(appProperties.baseUrl()).thenReturn("http://localhost:8080");
        ShortUrl shortUrl = mock(ShortUrl.class);
        when(shortUrl.getCode()).thenReturn("ABCD");
        when(shortUrl.getTargetUrl()).thenReturn("https://example.com/page");
        when(shortUrl.getCreatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(service.getByCode("ABCD")).thenReturn(shortUrl);

        mockMvc.perform(get("/api/v1/short-urls/ABCD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ABCD"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/ABCD"))
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/page"));
    }

    @Test
    void returnsNotFoundWhenCodeIsUnknown() throws Exception {
        when(service.getByCode("ZZZZ")).thenThrow(new ShortUrlNotFoundException("ZZZZ"));

        mockMvc.perform(get("/api/v1/short-urls/ZZZZ")).andExpect(status().isNotFound());
    }
}
