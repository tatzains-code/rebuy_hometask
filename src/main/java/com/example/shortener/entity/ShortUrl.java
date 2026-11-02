package com.example.shortener.entity;

import com.example.shortener.repository.ShortUrlRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Always inserted via {@link ShortUrlRepository#insertIfAbsent}, never via {@code save()}. */
@Entity
@Table(name = "short_url")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShortUrl {

    @Id
    private Long id;

    @Column(length = 4, nullable = false)
    private String code;

    @Column(name = "target_url", columnDefinition = "text", nullable = false)
    private String targetUrl;

    @Column(name = "url_hash", columnDefinition = "bytea", nullable = false)
    private byte[] urlHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
