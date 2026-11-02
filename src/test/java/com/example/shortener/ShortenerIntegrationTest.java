package com.example.shortener;

import com.example.shortener.repository.ShortUrlRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end coverage against a real, Flyway-migrated Postgres (via {@link TestcontainersConfiguration}). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShortenerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ShortUrlRepository repository;

    @Test
    void flywayMigrationApplied() {
        // A query against short_url succeeding at all proves V1 ran; nothing else creates this table.
        assertThatCode(repository::count).doesNotThrowAnyException();
    }

    @Test
    void createThenRedirectRoundTrip() throws Exception {
        String body = mockMvc.perform(post("/api/v1/short-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.google.com/integration-test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.code");

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.google.com/integration-test"));
    }

    @Test
    void duplicateSubmissionReturnsTheSameCode() throws Exception {
        String raw = "{\"url\":\"https://www.google.com/dedup-integration-test\"}";

        String first = mockMvc.perform(post("/api/v1/short-urls").contentType(MediaType.APPLICATION_JSON).content(raw))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstCode = JsonPath.read(first, "$.code");

        mockMvc.perform(post("/api/v1/short-urls").contentType(MediaType.APPLICATION_JSON).content(raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(firstCode));
    }

    @Test
    void validationErrorReturnsProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/short-urls").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
