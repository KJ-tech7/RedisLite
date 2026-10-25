
package com.redislite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

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
}
