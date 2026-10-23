package com.smartparking.parkinglot.application.service;

import com.smartparking.parkinglot.application.dto.ParkingLotImageResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ParkingLotImageService {
    List<ParkingLotImageResponse> upload(Long merchantUserId, Long parkingLotId, List<MultipartFile> files);
    List<ParkingLotImageResponse> getImages(Long parkingLotId);
    StoredImage getContent(Long parkingLotId, Long imageId);
    void delete(Long merchantUserId, Long parkingLotId, Long imageId);
    ParkingLotImageResponse setCover(Long merchantUserId, Long parkingLotId, Long imageId);

    record StoredImage(Resource resource, String contentType, String originalFileName) {}
}
