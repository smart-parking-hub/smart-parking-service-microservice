package com.smartparking.parkinglot.infrastructure.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredObjectMetadata {
    private String objectKey;
    private String generation;
    private String checksumCrc32c;
    private Long sizeBytes;
    private String contentType;
}
