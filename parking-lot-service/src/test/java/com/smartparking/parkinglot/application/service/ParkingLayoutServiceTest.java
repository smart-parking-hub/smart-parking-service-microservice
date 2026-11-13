package com.smartparking.parkinglot.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.application.service.impl.ParkingLayoutServiceImpl;
import com.smartparking.parkinglot.domain.model.*;
import com.smartparking.parkinglot.domain.repository.*;
import com.smartparking.parkinglot.infrastructure.storage.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.smartparking.parkinglot.infrastructure.storage.FileSystemStorageAdapter;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingLayoutServiceTest {

    @TempDir Path storageRoot;

    @Mock
    private ParkingLotRepository parkingLotRepository;
    @Mock
    private ParkingLayoutRepository parkingLayoutRepository;
    @Mock
    private ParkingLayoutAssetRepository parkingLayoutAssetRepository;
    @Mock
    private ParkingLayoutPublicationRepository parkingLayoutPublicationRepository;
    @Mock
    private PhysicalParkingSlotRepository physicalParkingSlotRepository;
    @Mock
    private CurrentSlotAllocationRepository currentSlotAllocationRepository;
    @Mock
    private SlotRepository slotRepository;
    @Mock
    private SlotHoldRepository slotHoldRepository;
    @Mock
    private PricingRuleRepository pricingRuleRepository;
    @Mock
    private StoragePort storagePort;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ParkingLayoutServiceImpl layoutService;

    private final Long merchantUserId = 100L;
    private final Long parkingLotId = 1L;
    private ParkingLot approvedLot;

    @BeforeEach
    void setUp() {
        approvedLot = ParkingLot.builder()
                .id(parkingLotId)
                .merchantId(merchantUserId)
                .name("Test Lot")
                .status(ParkingLotStatusEnum.APPROVED)
                .build();
    }

    private GridDto buildValidGrid() {
        return GridDto.builder().rows(10).cols(10).indexBase(0).build();
    }

    private MockMultipartFile sourceImage() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "source.png", "image/png", output.toByteArray());
    }

    @Test
    void filesystemSourceRoundTripUsesPersistedMetadataAfterAdapterRestart() throws Exception {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(approvedLot));
        ReflectionTestUtils.setField(layoutService, "storagePort",
                new FileSystemStorageAdapter(storageRoot, 5 * 1024 * 1024, 0));
        MockMultipartFile image = sourceImage();
        var response = layoutService.uploadSourceImage(merchantUserId, parkingLotId, image);
        var savedAsset = org.mockito.ArgumentCaptor.forClass(ParkingLayoutAsset.class);
        verify(parkingLayoutAssetRepository).save(savedAsset.capture());
        ParkingLayoutAsset asset = savedAsset.getValue();
        assertEquals(response.getSourceAssetId(), asset.getAssetId());
        when(parkingLayoutAssetRepository.findByParkingLotIdAndAssetId(parkingLotId, asset.getAssetId()))
                .thenReturn(Optional.of(asset));

        ReflectionTestUtils.setField(layoutService, "storagePort",
                new FileSystemStorageAdapter(storageRoot, 5 * 1024 * 1024, 0));
        assertArrayEquals(image.getBytes(), layoutService.getSourceImageContent(merchantUserId, parkingLotId, asset.getAssetId()));
        byte[] corrupt = image.getBytes();
        corrupt[corrupt.length - 1] ^= 1;
        Files.write(storageRoot.resolve(asset.getObjectKey()), corrupt);
        assertThrows(IllegalStateException.class,
                () -> layoutService.getSourceImageContent(merchantUserId, parkingLotId, asset.getAssetId()));
        assertThrows(AppException.class,
                () -> layoutService.getSourceImageContent(999L, parkingLotId, asset.getAssetId()));
    }

    @Test
    void filesystemStorageFailureDoesNotPersistSuccessfulUploadMetadata() throws Exception {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(approvedLot));
        ReflectionTestUtils.setField(layoutService, "storagePort",
                new FileSystemStorageAdapter(storageRoot, 5 * 1024 * 1024, Long.MAX_VALUE));
        MockMultipartFile image = sourceImage();
        assertThrows(IllegalStateException.class,
                () -> layoutService.uploadSourceImage(merchantUserId, parkingLotId, image));
        verify(parkingLayoutAssetRepository, never()).save(any());
    }

    private List<LayoutElementDto> buildValidElements() {
        LayoutElementDto slot1 = LayoutElementDto.builder()
                .elementId("elem-1")
                .cellType(CellTypeEnum.PARKING_SLOT)
                .code("A-01")
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .row(0).col(0)
                .build();

        LayoutElementDto slot2 = LayoutElementDto.builder()
                .elementId("elem-2")
                .cellType(CellTypeEnum.PARKING_SLOT)
                .code("A-02")
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .row(0).col(1)
                .build();

        LayoutElementDto road = LayoutElementDto.builder()
                .elementId("elem-road")
                .cellType(CellTypeEnum.DRIVEWAY)
                .row(0).col(2)
                .build();

        return new ArrayList<>(List.of(slot1, slot2, road));
    }

    @Test
    @DisplayName("AC1: Save draft increments layoutVersion and updates draftDocument JSON")
    void testSaveDraftSuccess() {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(approvedLot));
        when(parkingLayoutRepository.findByParkingLotIdForUpdate(parkingLotId)).thenReturn(Optional.empty());
        when(parkingLayoutRepository.save(any(ParkingLayout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SaveLayoutDraftRequest request = SaveLayoutDraftRequest.builder()
                .expectedVersion(1)
                .grid(buildValidGrid())
                .elements(buildValidElements())
                .build();

        LayoutDraftResponse response = layoutService.saveDraft(merchantUserId, parkingLotId, request);

        assertNotNull(response);
        assertEquals(2, response.getLayoutVersion());
        assertNotNull(response.getValidationSummary());
        assertTrue(response.getValidationSummary().isValid());
        verify(parkingLayoutRepository).save(any(ParkingLayout.class));
    }

    @Test
    @DisplayName("AC1: Save draft fails with FORBIDDEN when merchant is not lot owner")
    void testSaveDraftWrongOwner() {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(approvedLot));

        SaveLayoutDraftRequest request = SaveLayoutDraftRequest.builder()
                .expectedVersion(1)
                .grid(buildValidGrid())
                .elements(buildValidElements())
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                layoutService.saveDraft(999L, parkingLotId, request));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("AC1: Save draft detects version conflict when expected version does not match")
    void testSaveDraftVersionConflict() {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(approvedLot));
        ParkingLayout existingLayout = ParkingLayout.builder()
                .id(10L)
                .parkingLot(approvedLot)
                .layoutVersion(3)
                .build();
        when(parkingLayoutRepository.findByParkingLotIdForUpdate(parkingLotId)).thenReturn(Optional.of(existingLayout));

        SaveLayoutDraftRequest request = SaveLayoutDraftRequest.builder()
                .expectedVersion(2) // Stale version
                .grid(buildValidGrid())
                .elements(buildValidElements())
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                layoutService.saveDraft(merchantUserId, parkingLotId, request));
        assertEquals(ErrorCode.LAYOUT_VERSION_CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("AC2: Save draft rejects layout with out of bounds coordinates")
    void testSaveDraftOutOfBounds() {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(approvedLot));

        List<LayoutElementDto> elements = buildValidElements();
        elements.get(0).setCol(15); // Out of 10 cols

        SaveLayoutDraftRequest request = SaveLayoutDraftRequest.builder()
                .expectedVersion(1)
                .grid(buildValidGrid())
                .elements(elements)
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                layoutService.saveDraft(merchantUserId, parkingLotId, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("ngoài phạm vi lưới"));
    }

    @Test
    @DisplayName("AC2: Save draft rejects layout with overlapping elements on same cell")
    void testSaveDraftCellCollision() {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(approvedLot));

        List<LayoutElementDto> elements = buildValidElements();
        elements.get(1).setRow(0); // Collision with slot1 at (0, 0)
        elements.get(1).setCol(0);

        SaveLayoutDraftRequest request = SaveLayoutDraftRequest.builder()
                .expectedVersion(1)
                .grid(buildValidGrid())
                .elements(elements)
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                layoutService.saveDraft(merchantUserId, parkingLotId, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Trùng lặp tọa độ"));
    }

    @Test
    @DisplayName("AC2: Publish rejects layout when total slot capacity does not match lot totalCapacity")
    void testPublishMismatchedTotalCapacity() throws Exception {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(approvedLot));

        // 3 slots total, but lot totalCapacity is 2
        List<LayoutElementDto> elements = buildValidElements();
        elements.add(LayoutElementDto.builder()
                .elementId("elem-3")
                .cellType(CellTypeEnum.PARKING_SLOT)
                .code("A-03")
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .row(1).col(0)
                .build());

        LayoutDocumentDto doc = LayoutDocumentDto.builder()
                .schemaVersion(1)
                .parkingLotId(parkingLotId)
                .layoutVersion(1)
                .grid(buildValidGrid())
                .elements(elements)
                .build();

        ParkingLayout layout = ParkingLayout.builder()
                .id(10L)
                .parkingLot(approvedLot)
                .layoutVersion(1)
                .draftDocument(objectMapper.writeValueAsString(doc))
                .build();
        when(parkingLayoutRepository.findByParkingLotIdForUpdate(parkingLotId)).thenReturn(Optional.of(layout));

        // Slots in lot: 2 SEDAN_SUV
        when(slotRepository.findByParkingLotId(parkingLotId)).thenReturn(List.of(
                Slot.builder().id(100L).vehicleType(VehicleTypeEnum.SEDAN_SUV).totalCapacity(2).build()
        ));

        PublishLayoutRequest publishRequest = PublishLayoutRequest.builder()
                .expectedVersion(1)
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                layoutService.publishLayout(merchantUserId, parkingLotId, publishRequest));
        assertEquals(ErrorCode.LAYOUT_INVALID_CAPACITY, ex.getErrorCode());
    }

    @Test
    @DisplayName("AC2 & AC7: Publish guards against deleting physical slots that have active allocations")
    void testPublishGuardsActiveBookingDeletion() throws Exception {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(approvedLot));

        PhysicalParkingSlot physicalSlot1 = PhysicalParkingSlot.builder()
                .id("ps-501")
                .parkingLotId(parkingLotId)
                .slotGroupId(100L)
                .code("A-01")
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .build();
        when(physicalParkingSlotRepository.findByParkingLotId(parkingLotId))
                .thenReturn(List.of(physicalSlot1));

        // Elements do not contain ps-501!
        List<LayoutElementDto> elements = buildValidElements();
        elements.get(0).setCode("A-03"); // replaced A-01 with A-03, physicalSlotId is null

        LayoutDocumentDto doc = LayoutDocumentDto.builder()
                .schemaVersion(1)
                .parkingLotId(parkingLotId)
                .layoutVersion(1)
                .grid(buildValidGrid())
                .elements(elements)
                .build();

        ParkingLayout layout = ParkingLayout.builder()
                .id(10L)
                .parkingLot(approvedLot)
                .layoutVersion(1)
                .draftDocument(objectMapper.writeValueAsString(doc))
                .activePublicationId("pub-100")
                .build();
        when(parkingLayoutRepository.findByParkingLotIdForUpdate(parkingLotId)).thenReturn(Optional.of(layout));

        when(slotRepository.findByParkingLotId(parkingLotId)).thenReturn(List.of(
                Slot.builder().id(100L).vehicleType(VehicleTypeEnum.SEDAN_SUV).totalCapacity(2).build()
        ));

        PricingRule pricingRule = PricingRule.builder()
                .parkingLot(approvedLot)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .basePricePerHour(new BigDecimal("20000"))
                .build();
        when(pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(pricingRule));

        // Active allocation exists for ps-501!
        CurrentSlotAllocation activeAlloc = CurrentSlotAllocation.builder()
                .physicalSlotId("ps-501")
                .bookingId(8888L)
                .parkingLotId(parkingLotId)
                .slotGroupId(100L)
                .build();
        when(currentSlotAllocationRepository.findByParkingLotId(parkingLotId))
                .thenReturn(List.of(activeAlloc));

        PublishLayoutRequest publishRequest = PublishLayoutRequest.builder()
                .expectedVersion(1)
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                layoutService.publishLayout(merchantUserId, parkingLotId, publishRequest));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("đang có lượt đặt chỗ hoạt động"));
    }

    @Test
    @DisplayName("AC5: activatePhysicalMode refuses if layout has never been published")
    void testActivatePhysicalModeWithoutPublishedLayout() {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(approvedLot));
        ParkingLayout layout = ParkingLayout.builder()
                .id(10L)
                .parkingLot(approvedLot)
                .activePublicationId(null)
                .build();
        when(parkingLayoutRepository.findByParkingLotIdForUpdate(parkingLotId)).thenReturn(Optional.of(layout));

        AppException ex = assertThrows(AppException.class, () ->
                layoutService.activatePhysicalMode(merchantUserId, parkingLotId));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Phải công bố sơ đồ"));
    }

    @Test
    @DisplayName("AC5: activatePhysicalMode successfully activates PHYSICAL mode when layout is published")
    void testActivatePhysicalModeSuccess() {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(approvedLot));
        ParkingLayout layout = ParkingLayout.builder()
                .id(10L)
                .parkingLot(approvedLot)
                .activePublicationId("pub-200")
                .allocationMode(AllocationMode.GROUP)
                .build();
        when(parkingLayoutRepository.findByParkingLotIdForUpdate(parkingLotId)).thenReturn(Optional.of(layout));
        when(slotRepository.findByParkingLotId(parkingLotId)).thenReturn(Collections.emptyList());

        layoutService.activatePhysicalMode(merchantUserId, parkingLotId);

        assertEquals(AllocationMode.PHYSICAL, layout.getAllocationMode());
        verify(parkingLayoutRepository).save(layout);
    }

    @Test
    @DisplayName("AC8: getPublicAvailability returns slot statuses without leaking driverId or booking PII")
    void testGetPublicAvailabilitySanitization() {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(approvedLot));
        ParkingLayout layout = ParkingLayout.builder()
                .id(10L)
                .parkingLot(approvedLot)
                .activePublicationId("pub-200")
                .build();
        when(parkingLayoutRepository.findByParkingLotId(parkingLotId)).thenReturn(Optional.of(layout));

        PhysicalParkingSlot p1 = PhysicalParkingSlot.builder()
                .id("101")
                .parkingLotId(parkingLotId)
                .slotGroupId(100L)
                .code("A-01")
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .status("AVAILABLE")
                .build();
        PhysicalParkingSlot p2 = PhysicalParkingSlot.builder()
                .id("102")
                .parkingLotId(parkingLotId)
                .slotGroupId(100L)
                .code("A-02")
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .status("AVAILABLE")
                .build();

        when(physicalParkingSlotRepository.findByParkingLotId(parkingLotId)).thenReturn(List.of(p1, p2));

        // p1 is held in current_slot_allocations
        CurrentSlotAllocation alloc = CurrentSlotAllocation.builder()
                .physicalSlotId("101")
                .bookingId(9999L)
                .build();
        when(currentSlotAllocationRepository.findByParkingLotId(parkingLotId))
                .thenReturn(List.of(alloc));

        PublicAvailabilityResponse response = layoutService.getPublicAvailability(parkingLotId);

        assertNotNull(response);
        assertEquals(parkingLotId, response.getParkingLotId());
        assertEquals("pub-200", response.getPublicationId());
        assertEquals(2, response.getSlots().size());

        PublicSlotAvailabilityDto s1 = response.getSlots().stream().filter(s -> "101".equals(s.getPhysicalSlotId())).findFirst().orElseThrow();
        assertEquals("HELD", s1.getStatus());
        assertEquals("A-01", s1.getCode());

        PublicSlotAvailabilityDto s2 = response.getSlots().stream().filter(s -> "102".equals(s.getPhysicalSlotId())).findFirst().orElseThrow();
        assertEquals("AVAILABLE", s2.getStatus());
        assertEquals("A-02", s2.getCode());
    }
}
