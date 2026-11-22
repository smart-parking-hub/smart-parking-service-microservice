package com.smartparking.parkinglot.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.application.service.ParkingLayoutService;
import com.smartparking.parkinglot.domain.model.*;
import com.smartparking.parkinglot.domain.repository.*;
import com.smartparking.parkinglot.infrastructure.config.ParkingLayoutProperties;
import com.smartparking.parkinglot.infrastructure.storage.StoragePort;
import com.smartparking.parkinglot.infrastructure.storage.StoredObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingLayoutServiceImpl implements ParkingLayoutService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MiB
    private static final int MAX_IMAGE_DIMENSION = 4096;

    private final ParkingLotRepository parkingLotRepository;
    private final SlotRepository slotRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final ParkingLayoutRepository parkingLayoutRepository;
    private final ParkingLayoutAssetRepository parkingLayoutAssetRepository;
    private final ParkingLayoutPublicationRepository parkingLayoutPublicationRepository;
    private final PhysicalParkingSlotRepository physicalParkingSlotRepository;
    private final CurrentSlotAllocationRepository currentSlotAllocationRepository;
    private final com.smartparking.parkinglot.application.service.ScheduledSlotService scheduledSlots;
    private final SlotHoldRepository slotHoldRepository;
    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private ParkingLayoutProperties parkingLayoutProperties;

    private void assertFeatureEnabled() {
        if (parkingLayoutProperties != null && !parkingLayoutProperties.isEnabled()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tính năng quản lý sơ đồ bãi đỗ xe đang tạm tắt");
        }
    }

    @Override
    public SourceAssetUploadResponse uploadSourceImage(Long merchantUserId, Long parkingLotId, MultipartFile file) {
        assertFeatureEnabled();
        ParkingLot lot = getOwnedParkingLot(merchantUserId, parkingLotId);
        assertLotActiveForLayout(lot);

        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "File ảnh tải lên không được để trống");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Dung lượng ảnh tối đa là 5 MiB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể đọc dữ liệu file tải lên");
        }

        String contentType = file.getContentType();
        String extension;
        if (isPng(bytes, contentType)) {
            contentType = "image/png";
            extension = ".png";
        } else if (isJpeg(bytes, contentType)) {
            contentType = "image/jpeg";
            extension = ".jpg";
        } else {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ chấp nhận file ảnh định dạng PNG hoặc JPEG hợp lệ");
        }

        int width;
        int height;
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "File ảnh bị lỗi hoặc không thể giải mã");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Định dạng ảnh không được hỗ trợ");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                width = reader.getWidth(0);
                height = reader.getHeight(0);
            } finally {
                reader.dispose();
            }

            if (width <= 0 || height <= 0) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Kích thước ảnh không hợp lệ");
            }
            if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Kích thước ảnh vượt quá giới hạn tối đa " + MAX_IMAGE_DIMENSION + "x" + MAX_IMAGE_DIMENSION + " pixel");
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Lỗi kiểm tra kích thước ảnh: " + e.getMessage());
        }

        String assetId = UUID.randomUUID().toString();
        String objectKey = "parking-layouts/" + parkingLotId + "/sources/" + assetId + extension;

        // Upload to storage outside DB transaction
        StoredObjectMetadata metadata = storagePort.putImmutable(objectKey, bytes, contentType);

        ParkingLayoutAsset asset = ParkingLayoutAsset.builder()
                .assetId(assetId)
                .parkingLotId(parkingLotId)
                .purpose("SOURCE")
                .objectKey(objectKey)
                .originalFileName(file.getOriginalFilename())
                .contentType(contentType)
                .sizeBytes(metadata.getSizeBytes())
                .generation(metadata.getGeneration())
                .checksumCrc32c(metadata.getChecksumCrc32c())
                .width(width)
                .height(height)
                .build();

        parkingLayoutAssetRepository.save(asset);

        return SourceAssetUploadResponse.builder()
                .sourceAssetId(assetId)
                .parkingLotId(parkingLotId)
                .storedFileName(objectKey)
                .originalFileName(file.getOriginalFilename())
                .contentType(contentType)
                .sizeBytes(metadata.getSizeBytes())
                .width(width)
                .height(height)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public LayoutDraftResponse getDraft(Long merchantUserId, Long parkingLotId) {
        ParkingLot lot = getOwnedParkingLot(merchantUserId, parkingLotId);
        ParkingLayout layout = parkingLayoutRepository.findByParkingLotId(parkingLotId)
                .orElse(null);

        LayoutDocumentDto doc = null;
        if (layout != null && StringUtils.hasText(layout.getDraftDocument())) {
            try {
                doc = objectMapper.readValue(layout.getDraftDocument(), LayoutDocumentDto.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse draft document for lot {}: {}", parkingLotId, e.getMessage());
            }
        }

        if (doc == null) {
            doc = LayoutDocumentDto.builder()
                    .schemaVersion(1)
                    .parkingLotId(parkingLotId)
                    .layoutVersion(layout != null ? layout.getLayoutVersion() : 1)
                    .grid(GridDto.builder().rows(10).cols(10).indexBase(0).build())
                    .elements(new ArrayList<>())
                    .build();
        }

        LayoutValidationSummaryDto summary = validateDraft(parkingLotId, doc);

        String sourceAssetId = doc.getSourceAssetId();
        if (sourceAssetId == null && layout != null) {
            sourceAssetId = layout.getSourceAssetId();
        }

        String sourceImageUrl = sourceAssetId != null
                ? "/api/v1/merchant/parking-lots/" + parkingLotId + "/layout/sources/" + sourceAssetId + "/content"
                : null;

        return LayoutDraftResponse.builder()
                .parkingLotId(parkingLotId)
                .layoutVersion(layout != null ? layout.getLayoutVersion() : 1)
                .activePublicationId(layout != null ? layout.getActivePublicationId() : null)
                .sourceAssetId(sourceAssetId)
                .sourceImageUrl(sourceImageUrl)
                .grid(doc.getGrid())
                .elements(doc.getElements())
                .validationSummary(summary)
                .updatedAt(layout != null ? layout.getUpdatedAt() : LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional
    public LayoutDraftResponse saveDraft(Long merchantUserId, Long parkingLotId, SaveLayoutDraftRequest request) {
        assertFeatureEnabled();
        ParkingLot lot = getOwnedParkingLot(merchantUserId, parkingLotId);
        assertLotActiveForLayout(lot);

        validateGridAndElements(request.getGrid(), request.getElements());

        ParkingLayout layout = parkingLayoutRepository.findByParkingLotIdForUpdate(parkingLotId)
                .orElseGet(() -> ParkingLayout.builder()
                        .parkingLot(lot)
                        .layoutVersion(1)
                        .allocationMode(AllocationMode.GROUP)
                        .build());

        if (layout.getId() != null && !layout.getLayoutVersion().equals(request.getExpectedVersion())) {
            throw new AppException(ErrorCode.LAYOUT_VERSION_CONFLICT,
                    "Phiên bản sơ đồ đã thay đổi (hiện tại: " + layout.getLayoutVersion()
                            + ", yêu cầu: " + request.getExpectedVersion() + "). Vui lòng tải lại dữ liệu mới nhất.");
        }

        LayoutDocumentDto doc = LayoutDocumentDto.builder()
                .schemaVersion(1)
                .parkingLotId(parkingLotId)
                .layoutVersion(layout.getLayoutVersion() + 1)
                .sourceAssetId(request.getSourceAssetId())
                .grid(request.getGrid())
                .elements(request.getElements())
                .build();

        String draftJson;
        try {
            draftJson = objectMapper.writeValueAsString(doc);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể mã hóa cấu trúc sơ đồ thành JSON: " + e.getMessage());
        }

        layout.setLayoutVersion(layout.getLayoutVersion() + 1);
        layout.setDraftDocument(draftJson);
        if (request.getSourceAssetId() != null) {
            layout.setSourceAssetId(request.getSourceAssetId());
        }
        ParkingLayout saved = parkingLayoutRepository.save(layout);

        LayoutValidationSummaryDto summary = validateDraft(parkingLotId, doc);

        String sourceImageUrl = saved.getSourceAssetId() != null
                ? "/api/v1/merchant/parking-lots/" + parkingLotId + "/layout/sources/" + saved.getSourceAssetId() + "/content"
                : null;

        return LayoutDraftResponse.builder()
                .parkingLotId(parkingLotId)
                .layoutVersion(saved.getLayoutVersion())
                .activePublicationId(saved.getActivePublicationId())
                .sourceAssetId(saved.getSourceAssetId())
                .sourceImageUrl(sourceImageUrl)
                .grid(doc.getGrid())
                .elements(doc.getElements())
                .validationSummary(summary)
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public PublishLayoutResponse publishLayout(Long merchantUserId, Long parkingLotId, PublishLayoutRequest request) {
        assertFeatureEnabled();
        ParkingLot lot = getOwnedParkingLot(merchantUserId, parkingLotId);
        assertLotActiveForLayout(lot);

        // 1. Idempotency check on requestId
        if (StringUtils.hasText(request.getRequestId())) {
            Optional<ParkingLayoutPublication> existing =
                    parkingLayoutPublicationRepository.findByParkingLotIdAndRequestId(parkingLotId, request.getRequestId());
            if (existing.isPresent()) {
                ParkingLayoutPublication pub = existing.get();
                if (!pub.getLayoutVersion().equals(request.getExpectedVersion())) {
                    throw new AppException(ErrorCode.CONFLICT,
                            "Yêu cầu publish trùng requestId nhưng khác expectedVersion");
                }
                Map<VehicleTypeEnum, Integer> summary = parseSlotsSummary(pub.getSlotsSummary());
                return PublishLayoutResponse.builder()
                        .publicationId(pub.getId())
                        .parkingLotId(parkingLotId)
                        .layoutVersion(pub.getLayoutVersion())
                        .publishedAt(pub.getPublishedAt())
                        .totalSlotsCount(pub.getTotalSlotsCount())
                        .slotsSummary(summary)
                        .storageKey(pub.getStorageKey())
                        .build();
            }
        }

        // 2. Lock layout and slots for update
        ParkingLayout layout = parkingLayoutRepository.findByParkingLotIdForUpdate(parkingLotId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Bãi đỗ xe chưa có bản nháp sơ đồ để công bố"));

        if (!layout.getLayoutVersion().equals(request.getExpectedVersion())) {
            throw new AppException(ErrorCode.LAYOUT_VERSION_CONFLICT,
                    "Phiên bản sơ đồ đã thay đổi (hiện tại: " + layout.getLayoutVersion()
                            + ", yêu cầu: " + request.getExpectedVersion() + "). Vui lòng tải lại dữ liệu.");
        }

        if (!StringUtils.hasText(layout.getDraftDocument())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Bản nháp sơ đồ rỗng, không thể công bố");
        }

        LayoutDocumentDto doc;
        try {
            doc = objectMapper.readValue(layout.getDraftDocument(), LayoutDocumentDto.class);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể đọc dữ liệu nháp sơ đồ: " + e.getMessage());
        }

        validateGridAndElements(doc.getGrid(), doc.getElements());

        // 3. Validate slots against configured capacity
        List<Slot> slots = slotRepository.findByParkingLotId(parkingLotId);
        Map<VehicleTypeEnum, Slot> slotMap = slots.stream()
                .collect(Collectors.toMap(Slot::getVehicleType, s -> s));

        Map<VehicleTypeEnum, Integer> elementSlotCounts = new HashMap<>();
        Set<String> normalizedCodes = new HashSet<>();
        Set<String> seenPhysicalIds = new HashSet<>();
        List<LayoutElementDto> parkingElements = new ArrayList<>();

        for (LayoutElementDto elem : doc.getElements()) {
            if (elem.getCellType() == CellTypeEnum.PARKING_SLOT) {
                if (elem.getVehicleType() == null) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Ô đỗ xe '" + elem.getElementId() + "' thiếu loại xe (vehicleType)");
                }
                if (!StringUtils.hasText(elem.getCode())) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Ô đỗ xe '" + elem.getElementId() + "' thiếu mã ô (code)");
                }

                String trimmedCode = elem.getCode().trim();
                elem.setCode(trimmedCode);
                String normalized = trimmedCode.toLowerCase();
                if (!normalizedCodes.add(normalized)) {
                    throw new AppException(ErrorCode.CONFLICT, "Mã ô đỗ '" + trimmedCode + "' bị trùng lặp trong sơ đồ");
                }

                if (StringUtils.hasText(elem.getPhysicalSlotId())) {
                    if (!seenPhysicalIds.add(elem.getPhysicalSlotId())) {
                        throw new AppException(ErrorCode.BAD_REQUEST, "Trùng lặp physicalSlotId trong sơ đồ: " + elem.getPhysicalSlotId());
                    }
                }

                Slot slotGroup = slotMap.get(elem.getVehicleType());
                if (slotGroup == null) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Bãi đỗ xe chưa cấu hình sức chứa cho loại xe " + elem.getVehicleType());
                }
                elem.setSlotGroupId(slotGroup.getId());

                elementSlotCounts.merge(elem.getVehicleType(), 1, Integer::sum);
                parkingElements.add(elem);
            } else {
                // Non-parking elements must not have capacity bindings
                elem.setPhysicalSlotId(null);
                elem.setSlotGroupId(null);
                elem.setVehicleType(null);
                elem.setCode(null);
            }
        }

        // Verify count per vehicleType matches totalCapacity
        for (Slot slot : slots) {
            int actual = elementSlotCounts.getOrDefault(slot.getVehicleType(), 0);
            if (actual != slot.getTotalCapacity()) {
                throw new AppException(ErrorCode.LAYOUT_INVALID_CAPACITY,
                        "Số ô đỗ trên sơ đồ cho loại xe " + slot.getVehicleType() + " (" + actual
                                + ") không khớp với sức chứa cấu hình (" + slot.getTotalCapacity() + ")");
            }
        }
        for (var entry : elementSlotCounts.entrySet()) {
            if (!slotMap.containsKey(entry.getKey())) {
                throw new AppException(ErrorCode.LAYOUT_INVALID_CAPACITY,
                        "Sơ đồ chứa loại xe " + entry.getKey() + " chưa được cấu hình tại bãi");
            }
        }

        // Verify pricing rules exist for all vehicle types
        for (VehicleTypeEnum vt : elementSlotCounts.keySet()) {
            pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, vt)
                    .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                            "Bãi đỗ xe chưa cấu hình bảng giá cho loại xe " + vt));
        }

        scheduledSlots.assertLayoutSafe(parkingLotId);

        // 4. Physical slot preservation & active allocation guard
        List<CurrentSlotAllocation> activeAllocations = currentSlotAllocationRepository.findByParkingLotId(parkingLotId);
        Set<String> allocatedPhysicalSlotIds = activeAllocations.stream()
                .map(CurrentSlotAllocation::getPhysicalSlotId)
                .collect(Collectors.toSet());

        Map<String, PhysicalParkingSlot> existingSlots = physicalParkingSlotRepository.findByParkingLotId(parkingLotId).stream()
                .collect(Collectors.toMap(PhysicalParkingSlot::getId, s -> s));

        // R1: Client supplied physicalSlotId MUST already exist in DB for this lot, and must not be RETIRED
        for (LayoutElementDto elem : parkingElements) {
            String physId = elem.getPhysicalSlotId();
            if (StringUtils.hasText(physId)) {
                PhysicalParkingSlot existing = existingSlots.get(physId);
                if (existing == null) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã định danh ô vật lý không tồn tại hoặc không thuộc bãi: " + physId);
                }
                if ("RETIRED".equalsIgnoreCase(existing.getStatus())) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Ô đỗ vật lý đã bị thu hồi (RETIRED): " + physId);
                }
            }
        }

        // Ensure every active allocation's physical slot is still in the layout with the same vehicleType
        Set<String> layoutPhysicalSlotIds = parkingElements.stream()
                .map(LayoutElementDto::getPhysicalSlotId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (String activeId : allocatedPhysicalSlotIds) {
            if (!layoutPhysicalSlotIds.contains(activeId)) {
                PhysicalParkingSlot oldSlot = existingSlots.get(activeId);
                String code = oldSlot != null ? oldSlot.getCode() : activeId;
                throw new AppException(ErrorCode.CONFLICT,
                        "Ô đỗ " + code + " đang có lượt đặt chỗ hoạt động, không thể xóa khỏi sơ đồ");
            }
        }

        // R10: Do not change code or vehicleType of slots with active allocation or historical holds
        for (LayoutElementDto elem : parkingElements) {
            String physId = elem.getPhysicalSlotId();
            if (StringUtils.hasText(physId)) {
                PhysicalParkingSlot existing = existingSlots.get(physId);
                if (existing != null) {
                    boolean hasHistory = allocatedPhysicalSlotIds.contains(physId)
                            || slotHoldRepository.existsByPhysicalSlotId(physId);
                    if (hasHistory) {
                        if (!existing.getCode().equalsIgnoreCase(elem.getCode().trim())) {
                            throw new AppException(ErrorCode.CONFLICT,
                                    "Ô đỗ " + existing.getCode() + " đang có lượt đặt hoặc lịch sử giữ chỗ, không thể đổi mã ô");
                        }
                        if (existing.getVehicleType() != elem.getVehicleType()) {
                            throw new AppException(ErrorCode.CONFLICT,
                                    "Ô đỗ " + existing.getCode() + " đang có lượt đặt hoặc lịch sử giữ chỗ, không thể đổi loại xe");
                        }
                    }
                }
            }
        }

        // Assign UUID if null, ensure 1:1 mapping, and mark removed slots as RETIRED (R2)
        Set<String> publishedPhysicalSlotIds = new HashSet<>();
        List<PhysicalParkingSlot> toSaveSlots = new ArrayList<>();
        for (LayoutElementDto elem : parkingElements) {
            String physId = elem.getPhysicalSlotId();
            if (!StringUtils.hasText(physId)) {
                physId = UUID.randomUUID().toString();
                elem.setPhysicalSlotId(physId);
                PhysicalParkingSlot newSlot = PhysicalParkingSlot.builder()
                        .id(physId)
                        .parkingLotId(parkingLotId)
                        .slotGroupId(elem.getSlotGroupId())
                        .vehicleType(elem.getVehicleType())
                        .code(elem.getCode())
                        .normalizedCode(elem.getCode().toLowerCase())
                        .status("AVAILABLE")
                        .build();
                toSaveSlots.add(newSlot);
            } else {
                PhysicalParkingSlot existing = existingSlots.get(physId);
                existing.setSlotGroupId(elem.getSlotGroupId());
                existing.setVehicleType(elem.getVehicleType());
                existing.setCode(elem.getCode());
                existing.setNormalizedCode(elem.getCode().toLowerCase());
                existing.setStatus("AVAILABLE");
                toSaveSlots.add(existing);
            }
            publishedPhysicalSlotIds.add(physId);
        }

        for (PhysicalParkingSlot existingSlot : existingSlots.values()) {
            if (!publishedPhysicalSlotIds.contains(existingSlot.getId())) {
                existingSlot.setStatus("RETIRED");
                toSaveSlots.add(existingSlot);
            }
        }

        // R3: Public background image opt-in
        String publicAssetId = null;
        if (Boolean.TRUE.equals(request.getPublishBackgroundImage()) && StringUtils.hasText(layout.getSourceAssetId())) {
            var sourceAssetOpt = parkingLayoutAssetRepository.findByParkingLotIdAndAssetId(parkingLotId, layout.getSourceAssetId());
            if (sourceAssetOpt.isPresent()) {
                ParkingLayoutAsset sourceAsset = sourceAssetOpt.get();
                sourceAsset.setPurpose("PUBLIC_BACKGROUND");
                parkingLayoutAssetRepository.save(sourceAsset);
                publicAssetId = sourceAsset.getAssetId();
            }
        }

        // 5. Serialize canonical document
        doc.setLayoutVersion(request.getExpectedVersion());
        String canonicalJson;
        try {
            canonicalJson = objectMapper.writeValueAsString(doc);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi mã hóa canonical layout JSON: " + e.getMessage());
        }

        // 6. Persist an immutable JSON revision snapshot through the configured storage provider
        String publicationId = UUID.randomUUID().toString();
        String storageKey = "parking-layouts/" + parkingLotId + "/revisions/" + publicationId + ".json";
        byte[] jsonBytes = canonicalJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        StoredObjectMetadata jsonMeta = storagePort.putImmutable(storageKey, jsonBytes, "application/json");

        // R12: Persist revision JSON metadata asset record
        if (jsonMeta != null) {
            ParkingLayoutAsset jsonAsset = ParkingLayoutAsset.builder()
                    .assetId(publicationId)
                    .parkingLotId(parkingLotId)
                    .purpose("LAYOUT_REVISION_JSON")
                    .objectKey(storageKey)
                    .originalFileName(publicationId + ".json")
                    .contentType("application/json")
                    .sizeBytes(jsonMeta.getSizeBytes())
                    .generation(jsonMeta.getGeneration())
                    .checksumCrc32c(jsonMeta.getChecksumCrc32c())
                    .build();
            parkingLayoutAssetRepository.save(jsonAsset);
        }

        // 7. Persist physical slots, publication and update layout
        physicalParkingSlotRepository.saveAll(toSaveSlots);

        String slotsSummaryJson;
        try {
            slotsSummaryJson = objectMapper.writeValueAsString(elementSlotCounts);
        } catch (JsonProcessingException e) {
            slotsSummaryJson = "{}";
        }

        ParkingLayoutPublication publication = ParkingLayoutPublication.builder()
                .id(publicationId)
                .parkingLotId(parkingLotId)
                .layoutVersion(request.getExpectedVersion())
                .storageKey(storageKey)
                .sourceAssetId(publicAssetId)
                .gridRows(doc.getGrid().getRows())
                .gridCols(doc.getGrid().getCols())
                .totalSlotsCount(parkingElements.size())
                .slotsSummary(slotsSummaryJson)
                .canonicalDocument(canonicalJson)
                .requestId(request.getRequestId())
                .build();

        parkingLayoutPublicationRepository.save(publication);

        layout.setActivePublicationId(publicationId);
        layout.setLayoutVersion(request.getExpectedVersion() + 1);
        layout.setDraftDocument(canonicalJson);
        parkingLayoutRepository.save(layout);

        log.info("Published layout publication {} for lot {} with {} physical slots",
                publicationId, parkingLotId, parkingElements.size());

        return PublishLayoutResponse.builder()
                .publicationId(publicationId)
                .parkingLotId(parkingLotId)
                .layoutVersion(request.getExpectedVersion())
                .publishedAt(publication.getPublishedAt())
                .totalSlotsCount(parkingElements.size())
                .slotsSummary(elementSlotCounts)
                .storageKey(storageKey)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getSourceImageContent(Long merchantUserId, Long parkingLotId, String assetId) {
        getOwnedParkingLot(merchantUserId, parkingLotId);

        ParkingLayoutAsset asset = parkingLayoutAssetRepository.findByParkingLotIdAndAssetId(parkingLotId, assetId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy file ảnh nguồn"));

        return readAsset(asset);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicLayoutResponse getPublicLayout(Long parkingLotId) {
        ParkingLot lot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Bãi đỗ xe không tồn tại"));

        if (lot.getStatus() != ParkingLotStatusEnum.APPROVED) {
            throw new AppException(ErrorCode.LAYOUT_NOT_PUBLISHED, "Bãi đỗ xe chưa được phê duyệt hoạt động");
        }

        ParkingLayout layout = parkingLayoutRepository.findByParkingLotId(parkingLotId)
                .orElseThrow(() -> new AppException(ErrorCode.LAYOUT_NOT_PUBLISHED, "Bãi đỗ xe chưa công bố sơ đồ"));

        if (!StringUtils.hasText(layout.getActivePublicationId())) {
            throw new AppException(ErrorCode.LAYOUT_NOT_PUBLISHED, "Bãi đỗ xe chưa công bố sơ đồ");
        }

        ParkingLayoutPublication pub = parkingLayoutPublicationRepository.findById(layout.getActivePublicationId())
                .orElseThrow(() -> new AppException(ErrorCode.LAYOUT_NOT_PUBLISHED, "Không tìm thấy snapshot sơ đồ đã công bố"));

        LayoutDocumentDto doc;
        try {
            if (StringUtils.hasText(pub.getCanonicalDocument())) {
                doc = objectMapper.readValue(pub.getCanonicalDocument(), LayoutDocumentDto.class);
            } else {
                ParkingLayoutAsset revision = parkingLayoutAssetRepository
                        .findByParkingLotIdAndAssetId(parkingLotId, pub.getId())
                        .filter(asset -> pub.getStorageKey().equals(asset.getObjectKey())
                                && "LAYOUT_REVISION_JSON".equals(asset.getPurpose()))
                        .orElseThrow(() -> new IllegalStateException("Published revision metadata is missing"));
                byte[] bytes = readAsset(revision);
                doc = objectMapper.readValue(bytes, LayoutDocumentDto.class);
            }
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể đọc dữ liệu sơ đồ công bố: " + e.getMessage());
        }

        List<PricingRule> rules = pricingRuleRepository.findByParkingLotId(parkingLotId);
        List<PublicPricingDto> pricingOverlay = rules.stream()
                .map(r -> PublicPricingDto.builder()
                        .vehicleType(r.getVehicleType())
                        .basePricePerHour(r.getBasePricePerHour())
                        .currency("VND")
                        .billingUnit("HOUR")
                        .surgeMultiplier(r.getSurgeMultiplier())
                        .surgeStartTime(r.getSurgeStartTime())
                        .surgeEndTime(r.getSurgeEndTime())
                        .overtimePenaltyMultiplier(r.getOvertimePenaltyMultiplier())
                        .build())
                .collect(Collectors.toList());

        boolean hasBackground = StringUtils.hasText(pub.getSourceAssetId()) &&
                parkingLayoutAssetRepository.findByParkingLotIdAndAssetId(parkingLotId, pub.getSourceAssetId())
                        .map(a -> "PUBLIC_BACKGROUND".equalsIgnoreCase(a.getPurpose()))
                        .orElse(false);
        String backgroundUrl = hasBackground ? "/api/v1/parking-lots/" + parkingLotId + "/layout/background" : null;

        return PublicLayoutResponse.builder()
                .parkingLotId(parkingLotId)
                .publicationId(pub.getId())
                .grid(doc.getGrid())
                .elements(doc.getElements())
                .pricingOverlay(pricingOverlay)
                .hasBackgroundImage(hasBackground)
                .backgroundUrl(backgroundUrl)
                .publishedAt(pub.getPublishedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getPublicBackground(Long parkingLotId) {
        ParkingLot lot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Bãi đỗ xe không tồn tại"));

        if (lot.getStatus() != ParkingLotStatusEnum.APPROVED) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bãi đỗ xe chưa được phê duyệt hoạt động");
        }

        ParkingLayout layout = parkingLayoutRepository.findByParkingLotId(parkingLotId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Bãi đỗ xe chưa có sơ đồ"));

        if (!StringUtils.hasText(layout.getActivePublicationId())) {
            throw new AppException(ErrorCode.NOT_FOUND, "Bãi đỗ xe chưa công bố sơ đồ");
        }

        ParkingLayoutPublication pub = parkingLayoutPublicationRepository.findById(layout.getActivePublicationId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy snapshot sơ đồ đã công bố"));

        if (!StringUtils.hasText(pub.getSourceAssetId())) {
            throw new AppException(ErrorCode.NOT_FOUND, "Sơ đồ công bố không có ảnh nền công khai");
        }

        ParkingLayoutAsset asset = parkingLayoutAssetRepository.findByParkingLotIdAndAssetId(parkingLotId, pub.getSourceAssetId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy asset ảnh nguồn"));

        if (!"PUBLIC_BACKGROUND".equalsIgnoreCase(asset.getPurpose())) {
            throw new AppException(ErrorCode.NOT_FOUND, "Ảnh nguồn là tài liệu riêng tư, chưa được công khai làm ảnh nền");
        }

        return readAsset(asset);
    }

    private byte[] readAsset(ParkingLayoutAsset asset) {
        return storagePort.readVerified(StoredObjectMetadata.builder()
                .objectKey(asset.getObjectKey()).generation(asset.getGeneration())
                .checksumCrc32c(asset.getChecksumCrc32c()).sizeBytes(asset.getSizeBytes())
                .contentType(asset.getContentType()).build());
    }

    @Override
    @Transactional(readOnly = true)
    public PublicAvailabilityResponse getPublicAvailability(Long parkingLotId) {
        ParkingLot lot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Bãi đỗ xe không tồn tại"));

        if (lot.getStatus() != ParkingLotStatusEnum.APPROVED) {
            throw new AppException(ErrorCode.LAYOUT_NOT_PUBLISHED, "Bãi đỗ xe chưa được phê duyệt hoạt động");
        }

        ParkingLayout layout = parkingLayoutRepository.findByParkingLotId(parkingLotId)
                .orElseThrow(() -> new AppException(ErrorCode.LAYOUT_NOT_PUBLISHED, "Bãi đỗ xe chưa công bố sơ đồ"));

        if (!StringUtils.hasText(layout.getActivePublicationId())) {
            throw new AppException(ErrorCode.LAYOUT_NOT_PUBLISHED, "Bãi đỗ xe chưa công bố sơ đồ");
        }

        List<PhysicalParkingSlot> physicalSlots = physicalParkingSlotRepository.findByParkingLotId(parkingLotId);
        List<CurrentSlotAllocation> allocations = currentSlotAllocationRepository.findByParkingLotId(parkingLotId);
        Set<String> heldSlotIds = allocations.stream()
                .map(CurrentSlotAllocation::getPhysicalSlotId)
                .collect(Collectors.toSet());

        List<PublicSlotAvailabilityDto> slotDtos = physicalSlots.stream()
                .map(s -> {
                    String status;
                    if (heldSlotIds.contains(s.getId())) {
                        status = "HELD";
                    } else if ("AVAILABLE".equalsIgnoreCase(s.getStatus())) {
                        status = "AVAILABLE";
                    } else {
                        status = "UNAVAILABLE";
                    }
                    return PublicSlotAvailabilityDto.builder()
                            .physicalSlotId(s.getId())
                            .code(s.getCode())
                            .vehicleType(s.getVehicleType())
                            .status(status)
                            .build();
                })
                .collect(Collectors.toList());

        return PublicAvailabilityResponse.builder()
                .parkingLotId(parkingLotId)
                .publicationId(layout.getActivePublicationId())
                .updatedAt(LocalDateTime.now())
                .slots(slotDtos)
                .build();
    }

    @Override
    @Transactional
    public void activatePhysicalMode(Long merchantUserId, Long parkingLotId) {
        assertFeatureEnabled();
        ParkingLot lot = getOwnedParkingLot(merchantUserId, parkingLotId);
        assertLotActiveForLayout(lot);

        ParkingLayout layout = parkingLayoutRepository.findByParkingLotIdForUpdate(parkingLotId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Bãi đỗ xe chưa có sơ đồ để kích hoạt"));

        if (!StringUtils.hasText(layout.getActivePublicationId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Phải công bố sơ đồ hợp lệ trước khi kích hoạt chế độ chọn ô vật lý");
        }

        // Check no active unmapped holds (holds with physicalSlotId == null and released == false)
        List<Slot> lotSlots = slotRepository.findByParkingLotId(parkingLotId);
        for (Slot slot : lotSlots) {
            long unmappedHolds = slotHoldRepository.countBySlotIdAndReleasedFalseAndPhysicalSlotIdIsNull(slot.getId());
            if (unmappedHolds > 0) {
                throw new AppException(ErrorCode.CONFLICT,
                        "Không thể kích hoạt chế độ sơ đồ vật lý khi đang có " + unmappedHolds
                                + " lượt giữ chỗ cũ chưa hoàn tất");
            }
        }

        scheduledSlots.assertLayoutSafe(parkingLotId);
        layout.setAllocationMode(AllocationMode.PHYSICAL);
        parkingLayoutRepository.save(layout);
        log.info("Activated PHYSICAL allocation mode for parking lot {}", parkingLotId);
    }

    private void validateGridAndElements(GridDto grid, List<LayoutElementDto> elements) {
        if (grid == null || grid.getRows() == null || grid.getCols() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thiếu cấu hình kích thước lưới sơ đồ");
        }
        if (grid.getRows() <= 0 || grid.getRows() > 100 || grid.getCols() <= 0 || grid.getCols() > 100) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Kích thước lưới phải từ 1x1 đến 100x100");
        }

        if (elements == null) {
            return;
        }

        Set<String> elementIds = new HashSet<>();
        Set<String> coordinates = new HashSet<>();

        for (LayoutElementDto elem : elements) {
            if (elem.getElementId() == null || elem.getElementId().isBlank()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Phần tử sơ đồ thiếu elementId");
            }
            if (!elementIds.add(elem.getElementId())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "elementId bị trùng lặp: " + elem.getElementId());
            }

            if (elem.getRow() == null || elem.getCol() == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Phần tử '" + elem.getElementId() + "' thiếu tọa độ (row, col)");
            }
            if (elem.getRow() < 0 || elem.getRow() >= grid.getRows() ||
                    elem.getCol() < 0 || elem.getCol() >= grid.getCols()) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Tọa độ (" + elem.getRow() + ", " + elem.getCol() + ") của phần tử '"
                                + elem.getElementId() + "' nằm ngoài phạm vi lưới");
            }

            String coordKey = elem.getRow() + ":" + elem.getCol();
            if (!coordinates.add(coordKey)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Trùng lặp tọa độ (" + elem.getRow() + ", " + elem.getCol() + ") giữa các phần tử");
            }
        }
    }

    private LayoutValidationSummaryDto validateDraft(Long parkingLotId, LayoutDocumentDto doc) {
        List<Slot> slots = slotRepository.findByParkingLotId(parkingLotId);
        Map<VehicleTypeEnum, Integer> configuredCapacities = slots.stream()
                .collect(Collectors.toMap(Slot::getVehicleType, Slot::getTotalCapacity));

        Map<VehicleTypeEnum, Integer> layoutCounts = new HashMap<>();
        List<String> errors = new ArrayList<>();

        if (doc.getElements() != null) {
            for (LayoutElementDto elem : doc.getElements()) {
                if (elem.getCellType() == CellTypeEnum.PARKING_SLOT && elem.getVehicleType() != null) {
                    layoutCounts.merge(elem.getVehicleType(), 1, Integer::sum);
                }
            }
        }

        for (var entry : configuredCapacities.entrySet()) {
            int actual = layoutCounts.getOrDefault(entry.getKey(), 0);
            if (actual != entry.getValue()) {
                errors.add("Số ô xe " + entry.getKey() + " hiện có (" + actual + ") chưa khớp với sức chứa cấu hình (" + entry.getValue() + ")");
            }
        }

        return LayoutValidationSummaryDto.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .configuredCapacities(configuredCapacities)
                .layoutSlotCounts(layoutCounts)
                .build();
    }

    private ParkingLot getOwnedParkingLot(Long merchantUserId, Long parkingLotId) {
        ParkingLot lot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Bãi đỗ không tồn tại"));
        if (!lot.getMerchantId().equals(merchantUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền quản trị bãi đỗ xe này");
        }
        return lot;
    }

    private void assertLotActiveForLayout(ParkingLot lot) {
        if (lot.getStatus() == ParkingLotStatusEnum.REJECTED || lot.getStatus() == ParkingLotStatusEnum.SUSPENDED) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Bãi đỗ đang bị từ chối hoặc tạm ngưng hoạt động. Trạng thái hiện tại: " + lot.getStatus());
        }
    }

    private boolean isPng(byte[] bytes, String contentType) {
        if (bytes.length < 8) return false;
        return (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 &&
                bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47 &&
                bytes[4] == (byte) 0x0D && bytes[5] == (byte) 0x0A &&
                bytes[6] == (byte) 0x1A && bytes[7] == (byte) 0x0A);
    }

    private boolean isJpeg(byte[] bytes, String contentType) {
        if (bytes.length < 3) return false;
        return (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF);
    }

    @SuppressWarnings("unchecked")
    private Map<VehicleTypeEnum, Integer> parseSlotsSummary(String json) {
        if (!StringUtils.hasText(json)) return Collections.emptyMap();
        try {
            Map<String, Integer> raw = objectMapper.readValue(json, Map.class);
            Map<VehicleTypeEnum, Integer> result = new HashMap<>();
            for (var entry : raw.entrySet()) {
                result.put(VehicleTypeEnum.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
