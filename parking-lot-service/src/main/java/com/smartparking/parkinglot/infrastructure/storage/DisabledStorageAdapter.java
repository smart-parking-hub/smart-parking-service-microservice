package com.smartparking.parkinglot.infrastructure.storage;

/** Allows legacy startup with layout flags off, without accidentally accepting volatile uploads. */
public final class DisabledStorageAdapter implements StoragePort {
    private IllegalStateException disabled() {
        return new IllegalStateException("Parking layout storage is disabled; configure persistent storage first");
    }

    @Override public StoredObjectMetadata putImmutable(String key, byte[] bytes, String contentType) { throw disabled(); }
    @Override public byte[] readVerified(String key) { throw disabled(); }
    @Override public boolean exists(String key) { throw disabled(); }
    @Override public void deleteUnreferenced(String key) { throw disabled(); }
}
