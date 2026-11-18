package com.smartparking.parkinglot.infrastructure.cloudinary;

import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {

    boolean isAvailable();

    CloudinaryUploadResult upload(MultipartFile file, Long parkingLotId);

    void delete(String publicId);

    record CloudinaryUploadResult(String publicId, String secureUrl) {}
}
