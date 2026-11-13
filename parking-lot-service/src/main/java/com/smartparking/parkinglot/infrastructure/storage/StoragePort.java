package com.smartparking.parkinglot.infrastructure.storage;

public interface StoragePort {

    StoredObjectMetadata putImmutable(String objectKey, byte[] content, String contentType);

    byte[] readVerified(String objectKey);

    /** Read against metadata persisted by the application, not metadata inferred from the current file. */
    default byte[] readVerified(StoredObjectMetadata expected) {
        if (expected == null || expected.getObjectKey() == null || expected.getSizeBytes() == null
                || expected.getChecksumCrc32c() == null) {
            throw new IllegalStateException("Layout object verification metadata is missing");
        }
        byte[] bytes = readVerified(expected.getObjectKey());
        if (bytes.length != expected.getSizeBytes()
                || !StorageChecksums.crc32c(bytes).equals(expected.getChecksumCrc32c())) {
            throw new IllegalStateException("Layout object failed size/checksum verification");
        }
        return bytes;
    }

    boolean exists(String objectKey);

    void deleteUnreferenced(String objectKey);
}
