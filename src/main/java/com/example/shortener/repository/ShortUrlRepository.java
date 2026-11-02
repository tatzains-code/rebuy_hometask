package com.example.shortener.repository;

import com.example.shortener.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByCode(String code);

    Optional<ShortUrl> findByUrlHash(byte[] urlHash);

    @Query("select s.targetUrl from ShortUrl s where s.code = :code")
    Optional<String> findTargetUrlByCode(String code);

    @Query(value = "SELECT nextval('short_url_id_seq')", nativeQuery = true)
    long nextId();

    /** For {@link com.example.shortener.metrics.KeyspaceUsageGauge}; does not consume a value. */
    @Query(value = "SELECT last_value FROM short_url_id_seq", nativeQuery = true)
    long lastSequenceValue();

    @Modifying
    @Query(value = """
            INSERT INTO short_url (id, code, target_url, url_hash, created_at)
            VALUES (:id, :code, :targetUrl, :urlHash, :createdAt)
            ON CONFLICT (url_hash) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(long id, String code, String targetUrl, byte[] urlHash, Instant createdAt);
}
