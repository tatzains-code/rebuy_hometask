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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Not {@code @Transactional}: each thread needs its own request and its own
 * committed transaction, like real concurrent clients.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConcurrencyIntegrationTest {

    private static final int THREAD_COUNT = 50;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ShortUrlRepository repository;

    @Test
    void concurrentSubmissionsOfTheSameUrlProduceExactlyOneRowAndOneCode() throws Exception {
        long rowsBefore = repository.count();

        List<String> codes = runConcurrently(i -> "https://example.com/same-concurrency-target");

        assertThat(codes).hasSize(THREAD_COUNT);
        assertThat(Set.copyOf(codes)).hasSize(1);
        assertThat(repository.count()).isEqualTo(rowsBefore + 1);
    }

    @Test
    void concurrentSubmissionsOfDifferentUrlsProduceDistinctCodes() throws Exception {
        long rowsBefore = repository.count();

        List<String> codes = runConcurrently(i -> "https://example.com/distinct-concurrency-target-" + i);

        assertThat(codes).hasSize(THREAD_COUNT);
        assertThat(Set.copyOf(codes)).hasSize(THREAD_COUNT);
        assertThat(repository.count()).isEqualTo(rowsBefore + THREAD_COUNT);
    }

    /**
     * Releases all threads together via a shared latch, to maximize actual overlap, then resolves
     * each {@link Future} so a thread's real exception surfaces instead of being silently dropped.
     */
    private List<String> runConcurrently(IntFunction<String> urlFor) throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            int index = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return create(urlFor.apply(index));
            }));
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<String> codes = new ArrayList<>();
        for (Future<String> future : futures) {
            codes.add(future.get());
        }
        return codes;
    }

    private String create(String url) throws Exception {
        String body = mockMvc.perform(post("/api/v1/short-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.code");
    }
}
