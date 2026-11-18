package com.smartparking.parkinglot.infrastructure.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;
    private final String folderPrefix;

    public CloudinaryServiceImpl(
            @Autowired(required = false) Cloudinary cloudinary,
            @Value("${cloudinary.folder:smartparking/lots}") String folderPrefix) {
        this.cloudinary = cloudinary;
        this.folderPrefix = folderPrefix;
    }

    @Override
    public boolean isAvailable() {
        return cloudinary != null;
    }

    @Override
    public CloudinaryUploadResult upload(MultipartFile file, Long parkingLotId) {
        if (!isAvailable()) {
            throw new IllegalStateException("Cloudinary is not configured");
        }
        try {
            String targetFolder = folderPrefix + "/" + parkingLotId;
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", targetFolder,
                    "resource_type", "image",
                    "use_filename", false,
                    "unique_filename", true,
                    "overwrite", false
            ));

            String publicId = (String) uploadResult.get("public_id");
            String secureUrl = (String) uploadResult.get("secure_url");

            if (secureUrl != null && secureUrl.contains("/upload/")) {
                // Automatically enable auto format (WebP/AVIF) and auto quality compression
                secureUrl = secureUrl.replace("/upload/", "/upload/f_auto,q_auto/");
            }

            log.info("Uploaded image to Cloudinary successfully: publicId={}, secureUrl={}", publicId, secureUrl);
            return new CloudinaryUploadResult(publicId, secureUrl);
        } catch (IOException ex) {
            log.error("Failed to upload image to Cloudinary", ex);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi khi tải ảnh lên Cloudinary: " + ex.getMessage());
        }
    }

    @Override
    public void delete(String publicId) {
        if (!isAvailable() || publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Deleted image from Cloudinary: publicId={}, result={}", publicId, result.get("result"));
        } catch (Exception ex) {
            log.warn("Failed to delete image from Cloudinary with publicId: {}", publicId, ex);
        }
    }
}
