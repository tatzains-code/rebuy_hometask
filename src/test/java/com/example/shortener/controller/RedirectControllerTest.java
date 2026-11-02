package com.example.shortener.controller;

import com.example.shortener.metrics.ShortenerMetrics;
import com.example.shortener.service.TargetUrlLookup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TargetUrlLookup targetUrlLookup;
    @MockitoBean
    private ShortenerMetrics metrics;

    @Test
    void redirectsToTargetUrlWhenCodeIsFound() throws Exception {
        when(targetUrlLookup.findTargetUrl("ABCD")).thenReturn("https://example.com/page");

        mockMvc.perform(get("/ABCD"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page"))
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void returnsNotFoundWhenCodeIsUnknown() throws Exception {
        when(targetUrlLookup.findTargetUrl("ABCD")).thenReturn(null);

        mockMvc.perform(get("/ABCD")).andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForPathsNotMatchingTheCodeFormat() throws Exception {
        mockMvc.perform(get("/abc")).andExpect(status().isNotFound());
        mockMvc.perform(get("/abcde")).andExpect(status().isNotFound());
    }
}
