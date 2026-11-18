package com.smartparking.parkinglot.application.service.impl;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.parkinglot.application.dto.ParkingLotImageResponse;
import com.smartparking.parkinglot.application.service.ParkingLotImageService;
import com.smartparking.parkinglot.domain.model.ParkingLot;
import com.smartparking.parkinglot.domain.model.ParkingLotImage;
import com.smartparking.parkinglot.domain.repository.ParkingLotImageRepository;
import com.smartparking.parkinglot.domain.repository.ParkingLotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import com.smartparking.parkinglot.infrastructure.cloudinary.CloudinaryService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;

@Service
@Slf4j
public class ParkingLotImageServiceImpl implements ParkingLotImageService {

    private static final int MAX_IMAGES_PER_LOT = 5;
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");

    private final ParkingLotRepository parkingLotRepository;
    private final ParkingLotImageRepository imageRepository;
    private final Path storageRoot;
    private final CloudinaryService cloudinaryService;

    public ParkingLotImageServiceImpl(
            ParkingLotRepository parkingLotRepository,
            ParkingLotImageRepository imageRepository,
            @Value("${app.storage.parking-lot-images-dir:uploads/parking-lot-images}") String storageDirectory) {
        this(parkingLotRepository, imageRepository, storageDirectory, null);
    }

    @Autowired
    public ParkingLotImageServiceImpl(
            ParkingLotRepository parkingLotRepository,
            ParkingLotImageRepository imageRepository,
            @Value("${app.storage.parking-lot-images-dir:uploads/parking-lot-images}") String storageDirectory,
            @Autowired(required = false) CloudinaryService cloudinaryService) {
        this.parkingLotRepository = parkingLotRepository;
        this.imageRepository = imageRepository;
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
        this.cloudinaryService = cloudinaryService;
    }

    @Override
    @Transactional
    public List<ParkingLotImageResponse> upload(
            Long merchantUserId,
            Long parkingLotId,
            List<MultipartFile> files) {

        ParkingLot parkingLot = getOwnedParkingLot(merchantUserId, parkingLotId);
        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng chọn ít nhất một ảnh");
        }

        long existingCount = imageRepository.countByParkingLotId(parkingLotId);
        if (files.size() > MAX_IMAGES_PER_LOT || existingCount + files.size() > MAX_IMAGES_PER_LOT) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mỗi bãi đỗ chỉ được lưu tối đa 5 ảnh");
        }

        List<Path> writtenPaths = new ArrayList<>();
        List<String> uploadedCloudinaryPublicIds = new ArrayList<>();
        List<ParkingLotImageResponse> responses = new ArrayList<>();
        boolean useCloudinary = cloudinaryService != null && cloudinaryService.isAvailable();

        try {
            if (!useCloudinary) {
                Files.createDirectories(storageRoot);
            }

            for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename() == null ? "image.jpg" : file.getOriginalFilename();
                String ext = getFileExtension(originalFilename);
                if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT))) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ chấp nhận file ảnh (.jpg, .jpeg, .png, .webp)");
                }

                if (file.getSize() > MAX_FILE_SIZE) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mỗi ảnh không được vượt quá 5 MB");
                }

                String storedFileName;
                String imageUrl = null;

                if (useCloudinary) {
                    CloudinaryService.CloudinaryUploadResult cloudResult = cloudinaryService.upload(file, parkingLotId);
                    storedFileName = cloudResult.publicId();
                    imageUrl = cloudResult.secureUrl();
                    uploadedCloudinaryPublicIds.add(storedFileName);
                } else {
                    storedFileName = UUID.randomUUID() + ext;
                    Path destination = storageRoot.resolve(storedFileName);
                    try (InputStream input = file.getInputStream()) {
                        Files.copy(input, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    writtenPaths.add(destination);
                }

                ParkingLotImage saved = imageRepository.saveAndFlush(ParkingLotImage.builder()
                        .parkingLot(parkingLot)
                        .storedFileName(storedFileName)
                        .imageUrl(imageUrl)
                        .originalFileName(originalFilename)
                        .contentType(file.getContentType() != null ? file.getContentType() : "image/jpeg")
                        .sizeBytes(file.getSize())
                        .cover(existingCount == 0 && responses.isEmpty())
                        .build());
                responses.add(ParkingLotImageResponse.fromEntity(saved));
            }
            return responses;
        } catch (IOException exception) {
            cleanupFiles(writtenPaths);
            cleanupCloudinary(uploadedCloudinaryPublicIds);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh bãi đỗ: " + exception.getMessage());
        } catch (RuntimeException exception) {
            cleanupFiles(writtenPaths);
            cleanupCloudinary(uploadedCloudinaryPublicIds);
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParkingLotImageResponse> getImages(Long parkingLotId) {
        ensureParkingLotExists(parkingLotId);
        return imageRepository.findByParkingLotIdOrderByCoverDescCreatedAtAsc(parkingLotId).stream()
                .map(ParkingLotImageResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StoredImage getContent(Long parkingLotId, Long imageId) {
        ParkingLotImage image = getImage(parkingLotId, imageId);
        if (image.getImageUrl() != null && !image.getImageUrl().isBlank()) {
            return new StoredImage(null, image.getContentType(), image.getOriginalFileName(), image.getImageUrl());
        }
        try {
            Resource resource = new UrlResource(storageRoot.resolve(image.getStoredFileName()).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy file ảnh");
            }
            return new StoredImage(resource, image.getContentType(), image.getOriginalFileName());
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể đọc ảnh bãi đỗ");
        }
    }

    @Override
    @Transactional
    public void delete(Long merchantUserId, Long parkingLotId, Long imageId) {
        getOwnedParkingLot(merchantUserId, parkingLotId);
        ParkingLotImage image = getImage(parkingLotId, imageId);
        boolean deletedCover = image.isCover();

        imageRepository.delete(image);
        imageRepository.flush();

        if (deletedCover) {
            imageRepository.findFirstByParkingLotIdOrderByCreatedAtAsc(parkingLotId).ifPresent(next -> {
                next.setCover(true);
                imageRepository.save(next);
            });
        }

        if (image.getImageUrl() != null && cloudinaryService != null && cloudinaryService.isAvailable()) {
            cloudinaryService.delete(image.getStoredFileName());
        } else {
            Path storedFile = storageRoot.resolve(image.getStoredFileName());
            try {
                Files.deleteIfExists(storedFile);
            } catch (IOException exception) {
                log.warn("Failed to delete stored file: {}", storedFile);
            }
        }
    }

    @Override
    @Transactional
    public ParkingLotImageResponse setCover(Long merchantUserId, Long parkingLotId, Long imageId) {
        getOwnedParkingLot(merchantUserId, parkingLotId);
        ParkingLotImage selected = getImage(parkingLotId, imageId);
        List<ParkingLotImage> images = imageRepository.findByParkingLotIdOrderByCoverDescCreatedAtAsc(parkingLotId);
        images.forEach(image -> image.setCover(image.getId().equals(selected.getId())));
        imageRepository.saveAll(images);
        return ParkingLotImageResponse.fromEntity(selected);
    }

    private ParkingLot getOwnedParkingLot(Long merchantUserId, Long parkingLotId) {
        ParkingLot parkingLot = parkingLotRepository.findByIdForUpdate(parkingLotId)
                .orElseThrow(() -> new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Bãi đỗ không tồn tại"));
        if (!parkingLot.getMerchantId().equals(merchantUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền quản lý ảnh của bãi đỗ này");
        }
        return parkingLot;
    }

    private void ensureParkingLotExists(Long parkingLotId) {
        if (!parkingLotRepository.existsById(parkingLotId)) {
            throw new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Bãi đỗ không tồn tại");
        }
    }

    private ParkingLotImage getImage(Long parkingLotId, Long imageId) {
        return imageRepository.findByIdAndParkingLotId(imageId, parkingLotId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Ảnh bãi đỗ không tồn tại"));
    }

    private String getFileExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx);
    }

    private void cleanupFiles(List<Path> paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }

    private void cleanupCloudinary(List<String> publicIds) {
        if (cloudinaryService == null || !cloudinaryService.isAvailable() || publicIds == null) {
            return;
        }
        for (String id : publicIds) {
            try {
                cloudinaryService.delete(id);
            } catch (Exception ignored) {
            }
        }
    }
}
