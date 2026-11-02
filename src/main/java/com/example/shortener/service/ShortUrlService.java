package com.example.shortener.service;

import com.example.shortener.config.CodeProperties;
import com.example.shortener.entity.ShortUrl;
import com.example.shortener.exception.KeyspaceExhaustedException;
import com.example.shortener.exception.ShortUrlNotFoundException;
import com.example.shortener.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortUrlService {

    private static final String KEYSPACE_EXHAUSTED_SQLSTATE = "2200H";

    private final ShortUrlRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final CodeProperties codeProperties;
    private final UrlValidator urlValidator;

    @Transactional
    public CreateResult create(String rawUrl) {
        String normalizedUrl = urlValidator.normalize(rawUrl);
        byte[] urlHash = sha256(normalizedUrl);

        Optional<ShortUrl> existing = repository.findByUrlHash(urlHash);
        if (existing.isPresent()) {
            log.debug("Duplicate submission; returning existing code {}", existing.get().getCode());
            return new CreateResult(existing.get(), false);
        }

        long id = nextId();
        String code = codeGenerator.toCode(id);
        if (codeProperties.reserved().contains(code)) {
            id = nextId();
            code = codeGenerator.toCode(id);
        }

        int inserted = repository.insertIfAbsent(id, code, normalizedUrl, urlHash, Instant.now());
        if (inserted == 0) {
            log.debug("Lost the insert race for this URL; re-reading the row another request created");
            return new CreateResult(repository.findByUrlHash(urlHash).orElseThrow(), false);
        }

        ShortUrl saved = repository.findByCode(code).orElseThrow();
        log.info("Created short URL {} -> {}", saved.getCode(), saved.getTargetUrl());
        return new CreateResult(saved, true);
    }

    @Transactional(readOnly = true)
    public ShortUrl getByCode(String code) {
        return repository.findByCode(code).orElseThrow(() -> new ShortUrlNotFoundException(code));
    }

    private long nextId() {
        try {
            return repository.nextId();
        } catch (DataAccessException e) {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
            if (cause instanceof SQLException sqlException && KEYSPACE_EXHAUSTED_SQLSTATE.equals(sqlException.getSQLState())) {
                throw new KeyspaceExhaustedException();
            }
            throw e;
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
