package com.smartparking.parkinglot.application.service;

import com.smartparking.parkinglot.application.dto.*;
import org.springframework.web.multipart.MultipartFile;

public interface ParkingLayoutService {

    SourceAssetUploadResponse uploadSourceImage(Long merchantUserId, Long parkingLotId, MultipartFile file);

    LayoutDraftResponse getDraft(Long merchantUserId, Long parkingLotId);

    LayoutDraftResponse saveDraft(Long merchantUserId, Long parkingLotId, SaveLayoutDraftRequest request);

    PublishLayoutResponse publishLayout(Long merchantUserId, Long parkingLotId, PublishLayoutRequest request);

    byte[] getSourceImageContent(Long merchantUserId, Long parkingLotId, String assetId);

    PublicLayoutResponse getPublicLayout(Long parkingLotId);

    byte[] getPublicBackground(Long parkingLotId);

    PublicAvailabilityResponse getPublicAvailability(Long parkingLotId);

    void activatePhysicalMode(Long merchantUserId, Long parkingLotId);
}
