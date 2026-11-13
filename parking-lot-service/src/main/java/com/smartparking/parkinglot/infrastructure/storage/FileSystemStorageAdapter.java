package com.smartparking.parkinglot.infrastructure.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/** Private immutable objects on one trusted, service-owned filesystem. Not a multi-host object store. */
public final class FileSystemStorageAdapter implements StoragePort {
    private final Path root;
    private final int maxObjectBytes;
    private final long minFreeBytes;

    public FileSystemStorageAdapter(Path root, int maxObjectBytes, long minFreeBytes) {
        if (root == null || !root.isAbsolute() || root.normalize().getParent() == null) {
            throw new IllegalArgumentException("Layout storage requires an absolute, dedicated root directory");
        }
        if (maxObjectBytes <= 0 || maxObjectBytes == Integer.MAX_VALUE || minFreeBytes < 0) {
            throw new IllegalArgumentException("Invalid layout storage size/free-space limits");
        }
        this.root = root.normalize();
        this.maxObjectBytes = maxObjectBytes;
        this.minFreeBytes = minFreeBytes;
        try {
            validateDirectory(this.root);
            if (!Files.isWritable(this.root)) {
                throw new IllegalStateException("Layout storage root is not writable");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Layout storage root must already exist and be accessible", e);
        }
    }

    @Override
    public StoredObjectMetadata putImmutable(String objectKey, byte[] content, String contentType) {
        Path target = resolveKey(objectKey);
        if (content == null || content.length == 0 || content.length > maxObjectBytes) {
            throw new IllegalArgumentException("Layout object is empty or exceeds the storage size limit");
        }
        if (contentType == null || contentType.isBlank() || contentType.length() > 100) {
            throw new IllegalArgumentException("Layout object requires a valid content type");
        }
        // Prevent a caller changing the bytes between checksum calculation and disk write.
        byte[] snapshot = content.clone();
        StoredObjectMetadata metadata = StoredObjectMetadata.builder().objectKey(objectKey)
                .generation(StorageChecksums.sha256(snapshot)).checksumCrc32c(StorageChecksums.crc32c(snapshot))
                .sizeBytes((long) snapshot.length).contentType(contentType).build();
        Path staging = null;
        try {
            ensureParent(target.getParent());
            long usable = Files.getFileStore(root).getUsableSpace();
            if (usable < minFreeBytes || usable - minFreeBytes < snapshot.length) {
                throw new IllegalStateException("Insufficient free space for layout storage");
            }
            staging = Files.createTempFile(target.getParent(), ".upload-", ".part");
            try (FileChannel channel = FileChannel.open(staging, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(snapshot);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            validateDirectory(target.getParent());
            // A hard link atomically exposes completed bytes and fails if the target exists.
            // ATOMIC_MOVE is deliberately not used: its replacement behavior is provider-specific.
            Files.createLink(target, staging);
            return metadata;
        } catch (FileAlreadyExistsException e) {
            throw new IllegalStateException("Layout object already exists; overwrite is forbidden", e);
        } catch (IOException | UnsupportedOperationException e) {
            throw new IllegalStateException("Cannot publish layout object to filesystem storage", e);
        } finally {
            if (staging != null) {
                try {
                    Files.deleteIfExists(staging);
                } catch (IOException e) {
                    // The published object is intact. Leave only this temp file for operator cleanup.
                    org.slf4j.LoggerFactory.getLogger(FileSystemStorageAdapter.class)
                            .warn("Could not remove a layout upload temporary file", e);
                }
            }
        }
    }

    @Override
    public byte[] readVerified(String objectKey) {
        Path target = resolveKey(objectKey);
        try {
            validateDirectory(target.getParent());
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
                throw new IllegalStateException("Layout object is missing or is not a regular file");
            }
            try (var channel = Files.newByteChannel(target,
                    Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                 var input = Channels.newInputStream(channel)) {
                byte[] bytes = input.readNBytes(maxObjectBytes + 1);
                if (bytes.length == 0 || bytes.length > maxObjectBytes) {
                    throw new IllegalStateException("Layout object is empty or exceeds the storage size limit");
                }
                return bytes;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read layout object from filesystem storage", e);
        }
    }

    @Override
    public byte[] readVerified(StoredObjectMetadata expected) {
        byte[] bytes = StoragePort.super.readVerified(expected);
        if (!StorageChecksums.sha256(bytes).equals(expected.getGeneration())) {
            throw new IllegalStateException("Layout object failed generation verification");
        }
        return bytes;
    }

    @Override
    public boolean exists(String objectKey) {
        Path target = resolveKey(objectKey);
        try {
            // Validate existing ancestors even when the final parent/file does not exist yet.
            checkExistingAncestors(target);
            return Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot inspect layout object in filesystem storage", e);
        }
    }

    @Override
    public void deleteUnreferenced(String objectKey) {
        // The application/operator must prove the object is unreferenced before calling this method.
        Path target = resolveKey(objectKey);
        try {
            checkExistingAncestors(target);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Refusing to remove a non-regular layout object");
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot remove unreferenced layout object", e);
        }
    }

    private Path resolveKey(String key) {
        if (key == null || key.length() > 255 || key.isBlank() || key.startsWith("/")
                || key.contains("\\") || key.contains(":")) {
            throw new IllegalArgumentException("Invalid layout object key");
        }
        for (String part : key.split("/", -1)) {
            if (!part.matches("[A-Za-z0-9][A-Za-z0-9._-]*") || part.endsWith(".")
                    || part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
                throw new IllegalArgumentException("Invalid layout object key component");
            }
        }
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("Layout object key escapes storage root");
        }
        return target;
    }

    private void ensureParent(Path directory) throws IOException {
        validateDirectory(root);
        Path current = root;
        for (Path component : root.relativize(directory)) {
            current = current.resolve(component);
            try {
                Files.createDirectory(current);
            } catch (FileAlreadyExistsException ignored) {
                // Another upload may have created this directory; verify it rather than trusting it.
            }
            validateDirectory(current);
        }
    }

    private void validateDirectory(Path directory) throws IOException {
        checkExistingAncestors(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Layout storage directory is unavailable");
        }
    }

    private void checkExistingAncestors(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path component : path) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are forbidden in layout storage paths");
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !current.toRealPath().equals(current.toAbsolutePath().normalize())) {
                throw new IOException("Redirected filesystem paths are forbidden in layout storage");
            }
        }
    }
}
