package com.smartparking.parkinglot.application.dto;

import com.smartparking.parkinglot.domain.model.ParkingLotImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParkingLotImageResponse {
    private Long id;
    private Long parkingLotId;
    private String originalFileName;
    private String contentType;
    private Long sizeBytes;
    private boolean cover;
    private String contentUrl;
    private LocalDateTime createdAt;

    public static ParkingLotImageResponse fromEntity(ParkingLotImage image) {
        return ParkingLotImageResponse.builder()
                .id(image.getId())
                .parkingLotId(image.getParkingLot().getId())
                .originalFileName(image.getOriginalFileName())
                .contentType(image.getContentType())
                .sizeBytes(image.getSizeBytes())
                .cover(image.isCover())
                .contentUrl("/api/v1/parking-lots/" + image.getParkingLot().getId()
                        + "/images/" + image.getId() + "/content")
                .createdAt(image.getCreatedAt())
                .build();
    }
}
