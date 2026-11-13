package com.smartparking.parkinglot.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileSystemStorageAdapterTest {
    @TempDir Path root;
    private static final String KEY = "parking-layouts/1/revisions/revision-1.json";
    private static final byte[] BYTES = "{\"layoutVersion\":1}".getBytes(StandardCharsets.UTF_8);

    private FileSystemStorageAdapter adapter() {
        return new FileSystemStorageAdapter(root, 1024, 0);
    }

    @Test void persistsAcrossAdapterRestartAndVerifiesDatabaseMetadata() {
        StoredObjectMetadata metadata = adapter().putImmutable(KEY, BYTES, "application/json");
        assertEquals((long) BYTES.length, metadata.getSizeBytes());
        assertEquals(64, metadata.getGeneration().length());
        assertArrayEquals(BYTES, adapter().readVerified(metadata));
        try (var entries = assertDoesNotThrow(() -> Files.walk(root))) {
            assertFalse(entries.anyMatch(path -> path.toString().endsWith(".part")));
        }
    }

    @Test void doesNotOverwriteAnExistingObject() {
        var metadata = adapter().putImmutable(KEY, BYTES, "application/json");
        assertThrows(IllegalStateException.class, () -> adapter().putImmutable(KEY, new byte[]{42}, "image/png"));
        assertArrayEquals(BYTES, adapter().readVerified(metadata));
    }

    @Test void concurrentWritersHaveExactlyOneWinnerAndNeverPublishPartialBytes() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> publishAfter(start, BYTES));
            byte[] alternative = "another complete object".getBytes(StandardCharsets.UTF_8);
            var second = executor.submit(() -> publishAfter(start, alternative));
            start.countDown();
            StoredObjectMetadata a = first.get(10, TimeUnit.SECONDS);
            StoredObjectMetadata b = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(a == null, b == null, "Exactly one immutable write must succeed");
            assertArrayEquals(a != null ? BYTES : alternative, adapter().readVerified(a != null ? a : b));
        }
    }

    private StoredObjectMetadata publishAfter(CountDownLatch start, byte[] bytes) throws Exception {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            return adapter().putImmutable(KEY, bytes, "application/json");
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("already exists")) return null;
            throw e;
        }
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"../outside", "a/../../outside", "/absolute", "C:/absolute", "a\\b", "a//b",
            "a/./b", "a/../b", "a/", " file", "file.", "NUL", "a/CON.txt", "https://host/file"})
    void rejectsInvalidKeysForEveryOperation(String key) {
        StoragePort storage = adapter();
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> storage.putImmutable(key, BYTES, "application/json")),
                () -> assertThrows(IllegalArgumentException.class, () -> storage.readVerified(key)),
                () -> assertThrows(IllegalArgumentException.class, () -> storage.exists(key)),
                () -> assertThrows(IllegalArgumentException.class, () -> storage.deleteUnreferenced(key)));
    }

    @Test void rejectsCorruptedContentEvenWhenItsSizeIsUnchanged() throws IOException {
        var metadata = adapter().putImmutable(KEY, BYTES, "application/json");
        byte[] corrupt = BYTES.clone();
        corrupt[corrupt.length - 2] = '9';
        Files.write(root.resolve(KEY), corrupt);
        assertThrows(IllegalStateException.class, () -> adapter().readVerified(metadata));
    }

    @Test void rejectsWrongGenerationAndMissingVerificationMetadata() {
        var metadata = adapter().putImmutable(KEY, BYTES, "application/json");
        metadata.setGeneration("0".repeat(64));
        assertThrows(IllegalStateException.class, () -> adapter().readVerified(metadata));
        metadata.setChecksumCrc32c(null);
        assertThrows(IllegalStateException.class, () -> adapter().readVerified(metadata));
    }

    @Test void rejectsWrongDatabaseSize() {
        var metadata = adapter().putImmutable(KEY, BYTES, "application/json");
        metadata.setSizeBytes(metadata.getSizeBytes() + 1);
        assertThrows(IllegalStateException.class, () -> adapter().readVerified(metadata));
    }

    @Test void boundsWritesAndReads() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> adapter().putImmutable(KEY, new byte[1025], "image/png"));
        assertThrows(IllegalArgumentException.class, () -> adapter().putImmutable(KEY, new byte[0], "image/png"));
        adapter().putImmutable(KEY, BYTES, "application/json");
        Files.write(root.resolve(KEY), new byte[1025]);
        assertThrows(IllegalStateException.class, () -> adapter().readVerified(KEY));
    }

    @Test void insufficientSpaceFailsWithoutPublishingAnObject() {
        var storage = new FileSystemStorageAdapter(root, 1024, Long.MAX_VALUE);
        assertThrows(IllegalStateException.class, () -> storage.putImmutable(KEY, BYTES, "application/json"));
        assertFalse(storage.exists(KEY));
    }

    @Test void missingObjectFailsAndUnreferencedDeletionIsIdempotent() {
        StoragePort storage = adapter();
        assertFalse(storage.exists(KEY));
        assertThrows(IllegalStateException.class, () -> storage.readVerified(KEY));
        storage.putImmutable(KEY, BYTES, "application/json");
        assertTrue(storage.exists(KEY));
        storage.deleteUnreferenced(KEY);
        storage.deleteUnreferenced(KEY);
        assertFalse(storage.exists(KEY));
    }

    @Test void rejectsMissingRelativeAndNonDirectoryRoots() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new FileSystemStorageAdapter(Path.of("relative"), 10, 0));
        assertThrows(IllegalStateException.class, () -> new FileSystemStorageAdapter(root.resolve("missing"), 10, 0));
        Path file = Files.write(root.resolve("file"), new byte[]{1});
        assertThrows(IllegalStateException.class, () -> new FileSystemStorageAdapter(file, 10, 0));
    }

    @Test void rejectsLinkedDirectoriesForEveryOperation() throws IOException {
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path link = root.resolve("linked");
        createSymbolicLinkOrSkip(link, outside);
        Files.write(outside.resolve("object.json"), BYTES);
        String key = "linked/object.json";
        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> adapter().putImmutable(key, BYTES, "application/json")),
                () -> assertThrows(IllegalStateException.class, () -> adapter().readVerified(key)),
                () -> assertThrows(IllegalStateException.class, () -> adapter().exists(key)),
                () -> assertThrows(IllegalStateException.class, () -> adapter().deleteUnreferenced(key)));
        assertArrayEquals(BYTES, Files.readAllBytes(outside.resolve("object.json")));
    }

    @Test void rejectsLinkedFilesAndRoots() throws IOException {
        Path actual = Files.write(root.resolve("real.json"), BYTES);
        createSymbolicLinkOrSkip(root.resolve("linked.json"), actual);
        assertThrows(IllegalStateException.class, () -> adapter().readVerified("linked.json"));
        assertThrows(IllegalStateException.class, () -> adapter().deleteUnreferenced("linked.json"));
        Path directory = Files.createDirectory(root.resolve("actual-root"));
        createSymbolicLinkOrSkip(root.resolve("linked-root"), directory);
        assertThrows(IllegalStateException.class,
                () -> new FileSystemStorageAdapter(root.resolve("linked-root"), 1024, 0));
    }

    private void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            assumeTrue(false, "OS does not permit symbolic links for this test: " + e.getClass().getSimpleName());
        }
    }
}
