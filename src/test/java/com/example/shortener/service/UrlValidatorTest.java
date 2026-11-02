package com.example.shortener.service;

import com.example.shortener.config.AppProperties;
import com.example.shortener.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator(new AppProperties("http://localhost:8080"));

    @ParameterizedTest
    @CsvSource({
            "https://www.google.com/search, https://www.google.com/search",
            "HTTPS://WWW.GOOGLE.COM:443/search, https://www.google.com/search",
            "http://Example.COM:80/path, http://example.com/path",
            "https://example.com:8443/path, https://example.com:8443/path",
            "https://example.com/path?q=1#frag, https://example.com/path?q=1#frag"
    })
    void normalizesValidUrls(String raw, String expected) {
        assertThat(validator.normalize(raw)).isEqualTo(expected);
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(validator.normalize("  https://example.com/x  ")).isEqualTo("https://example.com/x");
    }

    @Test
    void preservesPathCase() {
        assertThat(validator.normalize("https://example.com/A"))
                .isNotEqualTo(validator.normalize("https://example.com/a"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "ftp://x.org", "not a url", "https://"})
    void rejectsInvalidUrls(String raw) {
        assertThatThrownBy(() -> validator.normalize(raw)).isInstanceOf(InvalidUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080/x", "HTTP://LOCALHOST:9999/x"})
    void rejectsUrlsPointingBackAtThisService(String raw) {
        assertThatThrownBy(() -> validator.normalize(raw)).isInstanceOf(InvalidUrlException.class);
    }
}
