
package com.redislite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RedisStore {

    private static class Entry {
        private final String value;
        private final long expiresAt;

        private Entry(String value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return expiresAt != 0
                    && System.currentTimeMillis() >= expiresAt;
        }
    }

    private final Map<String, Entry> data = new ConcurrentHashMap<>();
    private final Path dataFile;

    public RedisStore() throws IOException {
        this(Paths.get("data", "redis-lite.db"));
    }

    // Allows tests to use their own temporary data file.
    RedisStore(Path dataFile) throws IOException {
        this.dataFile = dataFile.toAbsolutePath();
        loadFromDisk();
    }

    public synchronized void set(
            String key, String value, long ttlSeconds) throws IOException {

        if (ttlSeconds < 0) {
            throw new IllegalArgumentException("TTL cannot be negative");
        }

        long expiresAt = 0;

        if (ttlSeconds > 0) {
            long now = System.currentTimeMillis();

            if (ttlSeconds > (Long.MAX_VALUE - now) / 1000) {
                throw new IllegalArgumentException("TTL is too large");
            }

            expiresAt = now + ttlSeconds * 1000;
        }

        data.put(key, new Entry(value, expiresAt));
        saveToDisk();
    }

    public synchronized String get(String key) throws IOException {
        Entry entry = data.get(key);

        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            data.remove(key);
            saveToDisk();
            return null;
        }

        return entry.value;
    }

    public synchronized long delete(String key) throws IOException {
        Entry removed = data.remove(key);

        if (removed == null) {
            return 0;
        }

        saveToDisk();
        return 1;
    }

    public synchronized long ttl(String key) throws IOException {
        Entry entry = data.get(key);

        if (entry == null) {
            return -2;
        }

        if (entry.isExpired()) {
            data.remove(key);
            saveToDisk();
            return -2;
        }

        if (entry.expiresAt == 0) {
            return -1;
        }

        return Math.max(
                0,
                (entry.expiresAt - System.currentTimeMillis()) / 1000
        );
    }

    private synchronized void saveToDisk() throws IOException {
        Files.createDirectories(dataFile.getParent());

        Path tempFile = dataFile.resolveSibling(
                dataFile.getFileName() + ".tmp"
        );

        try (var writer = Files.newBufferedWriter(
                tempFile, StandardCharsets.UTF_8)) {

            for (Map.Entry<String, Entry> item : data.entrySet()) {
                Entry entry = item.getValue();

                if (entry.isExpired()) {
                    continue;
                }

                String key = encode(item.getKey());
                String value = encode(entry.value);

                writer.write(entry.expiresAt + "\t" + key + "\t" + value);
                writer.newLine();
            }
        }

        try {
            Files.move(
                    tempFile,
                    dataFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(
                    tempFile,
                    dataFile,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void loadFromDisk() throws IOException {
        if (!Files.exists(dataFile)) {
            return;
        }

        long now = System.currentTimeMillis();

        for (String line : Files.readAllLines(
                dataFile, StandardCharsets.UTF_8)) {

            try {
                String[] parts = line.split("\t", 3);

                if (parts.length != 3) {
                    continue;
                }

                long expiresAt = Long.parseLong(parts[0]);
                String key = decode(parts[1]);
                String value = decode(parts[2]);

                if (expiresAt == 0 || expiresAt > now) {
                    data.put(key, new Entry(value, expiresAt));
                }
            } catch (IllegalArgumentException e) {
                System.err.println("Skipping an invalid record in the data file.");
            }
        }

        saveToDisk();
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String decode(String value) {
        return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8
        );
    }
}
