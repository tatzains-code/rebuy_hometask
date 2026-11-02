package com.example.shortener.service;

import com.example.shortener.config.CodeProperties;
import com.example.shortener.entity.ShortUrl;
import com.example.shortener.exception.KeyspaceExhaustedException;
import com.example.shortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    private static final String NORMALIZED_URL = "https://example.com/page";

    @Mock
    private ShortUrlRepository repository;
    @Mock
    private ShortCodeGenerator codeGenerator;
    @Mock
    private UrlValidator urlValidator;
    @Mock
    private CacheManager cacheManager;

    private ShortUrlService service;

    @BeforeEach
    void setUp() {
        CodeProperties codeProperties = new CodeProperties(9_132_277L, 4_861_105L, Set.of("RSVD"));
        service = new ShortUrlService(repository, codeGenerator, codeProperties, urlValidator, cacheManager);
        when(urlValidator.normalize(anyString())).thenReturn(NORMALIZED_URL);
    }

    @Test
    void returnsExistingShortUrlOnHashHit() {
        ShortUrl existing = mock(ShortUrl.class);
        when(repository.findByUrlHash(any())).thenReturn(Optional.of(existing));

        CreateResult result = service.create("https://example.com/page");

        assertThat(result.created()).isFalse();
        assertThat(result.shortUrl()).isEqualTo(existing);
        verify(repository, never()).insertIfAbsent(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void skipsReservedCodeAndRequestsAFreshId() {
        when(repository.findByUrlHash(any())).thenReturn(Optional.empty());
        when(repository.nextId()).thenReturn(1L, 2L);
        when(codeGenerator.toCode(1L)).thenReturn("RSVD");
        when(codeGenerator.toCode(2L)).thenReturn("ABCD");
        when(repository.insertIfAbsent(eq(2L), eq("ABCD"), eq(NORMALIZED_URL), any(), any(Instant.class)))
                .thenReturn(1);
        ShortUrl saved = mock(ShortUrl.class);
        when(repository.findByCode("ABCD")).thenReturn(Optional.of(saved));

        CreateResult result = service.create("https://example.com/page");

        assertThat(result.created()).isTrue();
        assertThat(result.shortUrl()).isEqualTo(saved);
        verify(repository, times(2)).nextId();
        verify(repository, never()).insertIfAbsent(eq(1L), any(), any(), any(), any());
    }

    @Test
    void writesThroughToTheCacheOnSuccessfulCreation() {
        when(repository.findByUrlHash(any())).thenReturn(Optional.empty());
        when(repository.nextId()).thenReturn(1L);
        when(codeGenerator.toCode(1L)).thenReturn("ABCD");
        when(repository.insertIfAbsent(eq(1L), eq("ABCD"), eq(NORMALIZED_URL), any(), any(Instant.class)))
                .thenReturn(1);
        ShortUrl saved = mock(ShortUrl.class);
        when(saved.getCode()).thenReturn("ABCD");
        when(saved.getTargetUrl()).thenReturn(NORMALIZED_URL);
        when(repository.findByCode("ABCD")).thenReturn(Optional.of(saved));
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache(TargetUrlLookup.CACHE_NAME)).thenReturn(cache);

        service.create("https://example.com/page");

        verify(cache).put("ABCD", NORMALIZED_URL);
    }

    @Test
    void reReadsExistingRowAfterLosingTheInsertRace() {
        when(repository.findByUrlHash(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mock(ShortUrl.class)));
        when(repository.nextId()).thenReturn(1L);
        when(codeGenerator.toCode(1L)).thenReturn("ABCD");
        when(repository.insertIfAbsent(eq(1L), eq("ABCD"), eq(NORMALIZED_URL), any(), any(Instant.class)))
                .thenReturn(0);

        CreateResult result = service.create("https://example.com/page");

        assertThat(result.created()).isFalse();
        verify(repository, times(2)).findByUrlHash(any());
    }

    @Test
    void translatesSqlState2200HToKeyspaceExhaustedException() {
        when(repository.findByUrlHash(any())).thenReturn(Optional.empty());
        SQLException sqlException = new SQLException("keyspace exhausted", "2200H");
        when(repository.nextId()).thenThrow(new InvalidDataAccessResourceUsageException("boom", sqlException));

        assertThatThrownBy(() -> service.create("https://example.com/page"))
                .isInstanceOf(KeyspaceExhaustedException.class);
    }
}
