package com.smartparking.parkinglot.infrastructure.storage;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.CRC32C;

final class StorageChecksums {
    private StorageChecksums() { }

    static String crc32c(byte[] bytes) {
        CRC32C checksum = new CRC32C();
        checksum.update(bytes);
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(4)
                .putInt((int) checksum.getValue()).array());
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable", e);
        }
    }
}
