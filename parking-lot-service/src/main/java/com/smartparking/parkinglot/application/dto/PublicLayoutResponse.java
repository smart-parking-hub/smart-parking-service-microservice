package com.smartparking.parkinglot.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicLayoutResponse {

    private Long parkingLotId;
    private String publicationId;
    private GridDto grid;
    private List<LayoutElementDto> elements;
    private List<PublicPricingDto> pricingOverlay;
    private boolean hasBackgroundImage;
    private String backgroundUrl;
    private LocalDateTime publishedAt;
}
