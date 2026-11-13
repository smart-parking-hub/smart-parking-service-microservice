package com.smartparking.parkinglot.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32C;
import java.util.Base64;

@Slf4j
public class MockStorageAdapter implements StoragePort {

    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();
    private final Map<String, StoredObjectMetadata> metadataMap = new ConcurrentHashMap<>();

    @Override
    public StoredObjectMetadata putImmutable(String objectKey, byte[] content, String contentType) {
        if (storage.containsKey(objectKey)) {
            throw new IllegalStateException("Object key already exists: immutable object cannot be overwritten: " + objectKey);
        }
        storage.put(objectKey, content.clone());

        CRC32C crc32c = new CRC32C();
        crc32c.update(content);
        long val = crc32c.getValue();
        byte[] crcBytes = new byte[]{
                (byte) ((val >> 24) & 0xff),
                (byte) ((val >> 16) & 0xff),
                (byte) ((val >> 8) & 0xff),
                (byte) (val & 0xff)
        };
        String checksum = Base64.getEncoder().encodeToString(crcBytes);

        StoredObjectMetadata metadata = StoredObjectMetadata.builder()
                .objectKey(objectKey)
                .generation(String.valueOf(System.currentTimeMillis()))
                .checksumCrc32c(checksum)
                .sizeBytes((long) content.length)
                .contentType(contentType)
                .build();

        metadataMap.put(objectKey, metadata);
        log.info("MockStorage stored object {} ({} bytes, crc32c={})", objectKey, content.length, checksum);
        return metadata;
    }

    @Override
    public byte[] readVerified(String objectKey) {
        byte[] data = storage.get(objectKey);
        if (data == null) {
            throw new IllegalArgumentException("Object not found in storage: " + objectKey);
        }
        return data.clone();
    }

    @Override
    public boolean exists(String objectKey) {
        return storage.containsKey(objectKey);
    }

    @Override
    public void deleteUnreferenced(String objectKey) {
        storage.remove(objectKey);
        metadataMap.remove(objectKey);
        log.info("MockStorage removed unreferenced object {}", objectKey);
    }
}
