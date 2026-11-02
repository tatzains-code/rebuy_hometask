package com.example.shortener.error;

import com.example.shortener.config.AppProperties;
import com.example.shortener.controller.RedirectController;
import com.example.shortener.controller.ShortUrlController;
import com.example.shortener.exception.InvalidUrlException;
import com.example.shortener.exception.KeyspaceExhaustedException;
import com.example.shortener.exception.ShortUrlNotFoundException;
import com.example.shortener.service.ShortUrlService;
import com.example.shortener.service.TargetUrlLookup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ShortUrlController.class, RedirectController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService service;
    @MockitoBean
    private AppProperties appProperties;
    @MockitoBean
    private TargetUrlLookup targetUrlLookup;

    @Test
    void methodArgumentNotValidReturnsValidationFailed() throws Exception {
        mockMvc.perform(post("/api/v1/short-urls").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("url"));
    }

    @Test
    void invalidUrlReturnsInvalidUrl() throws Exception {
        when(service.create(anyString())).thenThrow(new InvalidUrlException("not http(s)"));

        mockMvc.perform(post("/api/v1/short-urls").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"ftp://x.org\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_URL"))
                .andExpect(jsonPath("$.detail").value("not http(s)"));
    }

    @Test
    void malformedJsonReturnsMalformedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/short-urls").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    void shortUrlNotFoundReturnsJsonProblemByDefault() throws Exception {
        when(service.getByCode("ZZZZ")).thenThrow(new ShortUrlNotFoundException("ZZZZ"));

        mockMvc.perform(get("/api/v1/short-urls/ZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SHORT_URL_NOT_FOUND"));
    }

    @Test
    void shortUrlNotFoundReturnsHtmlWhenBrowserAsks() throws Exception {
        when(targetUrlLookup.findTargetUrl("ZZZZ")).thenReturn(null);

        mockMvc.perform(get("/ZZZZ").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("404")));
    }

    @Test
    void unmappedPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/some/totally/unmapped/path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/v1/short-urls"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/short-urls").contentType(MediaType.TEXT_PLAIN).content("hi"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void keyspaceExhaustedReturnsServiceUnavailable() throws Exception {
        when(service.create(anyString())).thenThrow(new KeyspaceExhaustedException());

        mockMvc.perform(post("/api/v1/short-urls").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("KEYSPACE_EXHAUSTED"));
    }

    @Test
    void dataAccessExceptionReturnsStorageUnavailable() throws Exception {
        when(service.create(anyString())).thenThrow(new DataAccessResourceFailureException("db is down"));

        mockMvc.perform(post("/api/v1/short-urls").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("STORAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("db is down"))));
    }

    @Test
    void unexpectedExceptionReturnsGenericInternalError() throws Exception {
        when(service.create(anyString())).thenThrow(new RuntimeException("secret internal detail"));

        mockMvc.perform(post("/api/v1/short-urls").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret internal detail"))));
    }
}
