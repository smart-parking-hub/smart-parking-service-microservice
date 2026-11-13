package com.smartparking.parkinglot.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class StoragePortTest {

    @Test
    @DisplayName("MockStorageAdapter stores object immutably and calculates crc32c checksum")
    void testMockStorageAdapterPutAndRead() {
        MockStorageAdapter adapter = new MockStorageAdapter();
        String key = "merchants/1/layouts/rev-1.json";
        byte[] content = "{\"version\":1}".getBytes(StandardCharsets.UTF_8);

        StoredObjectMetadata metadata = adapter.putImmutable(key, content, "application/json");
        assertNotNull(metadata);
        assertEquals(key, metadata.getObjectKey());
        assertEquals("application/json", metadata.getContentType());
        assertEquals((long) content.length, metadata.getSizeBytes());
        assertNotNull(metadata.getChecksumCrc32c());
        assertTrue(adapter.exists(key));

        byte[] retrieved = adapter.readVerified(key);
        assertArrayEquals(content, retrieved);
    }

    @Test
    @DisplayName("MockStorageAdapter rejects overwrite of immutable key")
    void testMockStorageAdapterRejectOverwrite() {
        MockStorageAdapter adapter = new MockStorageAdapter();
        String key = "merchants/1/layouts/rev-1.json";
        byte[] content = "{\"version\":1}".getBytes(StandardCharsets.UTF_8);

        adapter.putImmutable(key, content, "application/json");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                adapter.putImmutable(key, content, "application/json"));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("MockStorageAdapter throws IllegalArgumentException when reading missing object")
    void testMockStorageAdapterNotFound() {
        MockStorageAdapter adapter = new MockStorageAdapter();
        assertFalse(adapter.exists("non-existent"));

        assertThrows(IllegalArgumentException.class, () -> adapter.readVerified("non-existent"));
    }

    @Test
    @DisplayName("MockStorageAdapter deletes unreferenced object cleanly")
    void testMockStorageAdapterDelete() {
        MockStorageAdapter adapter = new MockStorageAdapter();
        String key = "temp/junk.json";
        adapter.putImmutable(key, "temp".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertTrue(adapter.exists(key));

        adapter.deleteUnreferenced(key);
        assertFalse(adapter.exists(key));
    }
}
