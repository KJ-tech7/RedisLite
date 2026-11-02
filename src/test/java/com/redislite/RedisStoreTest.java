
package com.redislite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class RedisStoreTest {

    @TempDir
    Path tempDir;

    private RedisStore createStore() throws IOException {
        return new RedisStore(tempDir.resolve("redis-lite-test.db"));
    }

    @Test
    void shouldStoreAndRetrieveValue() throws IOException {
        RedisStore store = createStore();
        store.set("name", "RedisLite", 0);
        assertEquals("RedisLite", store.get("name"));
    }

    @Test
    void shouldReturnNullForMissingKey() throws IOException {
        RedisStore store = createStore();
        assertNull(store.get("missing"));
    }

    @Test
    void shouldDeleteKey() throws IOException {
        RedisStore store = createStore();
        store.set("city", "Jaipur", 0);
        assertEquals(1, store.delete("city"));
        assertNull(store.get("city"));
    }

    @Test
    void shouldReturnZeroWhenDeletingMissingKey() throws IOException {
        RedisStore store = createStore();
        assertEquals(0, store.delete("missing"));
    }

    @Test
    void shouldOverwriteExistingValue() throws IOException {
        RedisStore store = createStore();
        store.set("color", "blue", 0);
        store.set("color", "green", 0);
        assertEquals("green", store.get("color"));
    }

    @Test
    void shouldKeepKeyWithoutExpiry() throws IOException {
        RedisStore store = createStore();
        store.set("permanent", "available", 0);
        assertEquals(-1, store.ttl("permanent"));
        assertEquals("available", store.get("permanent"));
    }

    @Test
    void shouldHandleTtl() throws IOException {
        RedisStore store = createStore();
        store.set("temporary", "value", 10);
        long ttl = store.ttl("temporary");
        assertTrue(ttl >= 0 && ttl <= 10);
    }

    @Test
    void shouldExpireKey() throws IOException, InterruptedException {
        RedisStore store = createStore();
        store.set("temporary", "value", 1);
        Thread.sleep(1200);
        assertNull(store.get("temporary"));
        assertEquals(-2, store.ttl("temporary"));
    }

    @Test
    void shouldRejectNegativeTtl() throws IOException {
        RedisStore store = createStore();
        assertThrows(
                IllegalArgumentException.class,
                () -> store.set("invalid", "value", -1)
        );
    }

    @Test
    void shouldRejectExcessivelyLargeTtl() throws IOException {
        RedisStore store = createStore();
        assertThrows(
                IllegalArgumentException.class,
                () -> store.set("invalid", "value", Long.MAX_VALUE)
        );
    }

    @Test
    void shouldHandleUnicodeAndSpecialCharacters() throws IOException {
        RedisStore store = createStore();
        String value = "Hello @#$% café नमस्ते";
        store.set("special", value, 0);
        assertEquals(value, store.get("special"));
    }

    @Test
    void shouldRestoreValueAfterReloadingStore() throws IOException {
        Path file = tempDir.resolve("persistent.db");
        RedisStore firstStore = new RedisStore(file);
        firstStore.set("language", "Java", 0);
        RedisStore secondStore = new RedisStore(file);
        assertEquals("Java", secondStore.get("language"));
    }

    @Test
    void shouldNotRestoreExpiredValue() throws IOException, InterruptedException {
        Path file = tempDir.resolve("expired.db");
        RedisStore firstStore = new RedisStore(file);
        firstStore.set("temporary", "old", 1);
        Thread.sleep(1200);
        RedisStore secondStore = new RedisStore(file);
        assertNull(secondStore.get("temporary"));
    }

    @Test
    void shouldPersistDeletion() throws IOException {
        Path file = tempDir.resolve("deletion.db");
        RedisStore firstStore = new RedisStore(file);
        firstStore.set("remove-me", "value", 0);
        assertEquals(1, firstStore.delete("remove-me"));
        RedisStore secondStore = new RedisStore(file);
        assertNull(secondStore.get("remove-me"));
    }

    @Test
    void shouldHandle50ConcurrentThreads() throws Exception {
        RedisStore store = createStore();

        int threadCount = 50;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                final int threadNumber = i;

                Future<Boolean> result = executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(10, TimeUnit.SECONDS)) {
                        return false;
                    }

                    String key = "key-" + threadNumber;
                    String value = "value-" + threadNumber;

                    store.set(key, value, 0);

                    return value.equals(store.get(key));
                });

                results.add(result);
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            for (Future<Boolean> result : results) {
                assertTrue(
                        result.get(30, TimeUnit.SECONDS),
                        "A concurrent SET/GET operation failed"
                );
            }

            assertEquals(50, results.size());

        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void shouldPreserveOriginalFileWhenLoadingMalformedRecords()
            throws IOException {

        Path file = tempDir.resolve("malformed.db");

        String validRecord =
                "0\t" +
                java.util.Base64.getEncoder().encodeToString(
                        "language".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                ) +
                "\t" +
                java.util.Base64.getEncoder().encodeToString(
                        "Java".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );

        String originalContent =
                validRecord + "\n" +
                "this-is-a-malformed-record\n";

        Files.writeString(file, originalContent);

        RedisStore store = new RedisStore(file);

        assertEquals("Java", store.get("language"));

        String contentAfterLoading = Files.readString(file);

        assertEquals(
                originalContent,
                contentAfterLoading,
                "Loading the database must not rewrite the original file"
        );
    }
}
